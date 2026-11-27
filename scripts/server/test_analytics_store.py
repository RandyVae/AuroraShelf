import json
import os
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import analytics_store as store


class AnalyticsStoreTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name)
        store.DB_PATH = str(root / 'analytics.db')
        store.DEVICES_FILE = str(root / 'registered_devices.json')
        store.INVITES_FILE = str(root / 'testflight_invites.json')
        store.PROVISIONED_UDIDS_FILE = str(root / 'provisioned_udids.json')
        store.PB_DB_PATH = str(root / 'pb_data.db')
        store.ADMIN_AUTH_FILE = str(root / 'admin_auth.json')
        store.MANIFEST_PLIST_PATH = str(root / 'manifest.plist')
        store.init_db()

    def tearDown(self):
        self.tmp.cleanup()

    def test_ingest_dedup_and_clamp_and_hkt_bucketing(self):
        # 2026-11-27 16:30:00 UTC -> 2026-11-28 00:30:00 HKT (UTC+8)
        ts_hkt_midnight = 1795797000.0
        payload = {
            'install_id': 'inst-iphone-001',
            'platform': 'ios',
            'device_form': 'phone',
            'device_model': 'iPhone18,2',
            'os_version': 'iOS 27.2',
            'app_version': '2.19.2',
            'app_build': 277,
            'install_channel': 'testflight',
            'account_email': 'randy@example.com',
            'events': [
                {
                    'event_id': 'ev-0001',
                    'type': 'session_start',
                    'session_id': 'sess-0001',
                    'section': 'video',
                    'sub_section': 'video_browse',
                    'duration_sec': 0,
                    'ts': ts_hkt_midnight,
                },
                {
                    'event_id': 'ev-0002',
                    'type': 'section_dwell',
                    'session_id': 'sess-0001',
                    'section': 'video',
                    'sub_section': 'video_play',
                    'duration_sec': 99999,  # clamped to 3600
                    'ts': ts_hkt_midnight + 120,
                },
                {
                    'event_id': 'ev-0003',
                    'type': 'section_dwell',
                    'session_id': 'sess-0001',
                    'section': 'invalid_sec',  # rejected cleanly
                    'duration_sec': 50,
                    'ts': ts_hkt_midnight + 130,
                },
            ],
        }
        ok, res, code = store.ingest_telemetry_batch(payload, client_ip='1.2.3.4', now_ts=ts_hkt_midnight + 150)
        self.assertTrue(ok)
        self.assertEqual(code, 200)
        self.assertEqual(res['accepted'], 2)
        self.assertEqual(res['rejected'], 1)
        self.assertEqual(res['deduped'], 0)

        # Replay identical batch -> must dedup and NOT double dwell
        ok2, res2, _ = store.ingest_telemetry_batch(payload, client_ip='1.2.3.4', now_ts=ts_hkt_midnight + 160)
        self.assertTrue(ok2)
        self.assertEqual(res2['accepted'], 0)
        self.assertEqual(res2['deduped'], 2)
        self.assertEqual(res2['rejected'], 1)

        overview = store.get_overview('today', 'all', True, now_ts=ts_hkt_midnight + 160)
        self.assertEqual(overview['kpis']['total_installs'], 1)
        self.assertEqual(overview['kpis']['online_now'], 1)
        self.assertEqual(overview['kpis']['total_dwell_sec'], 3600)
        self.assertEqual(overview['hourly_distribution'][0]['dwell_sec'], 3600)
        self.assertEqual(overview['sections_breakdown'][0]['section'], 'video')
        self.assertEqual(overview['sections_breakdown'][0]['dwell_sec'], 3600)

    def test_alias_preserved_across_telemetry_updates_and_sim_filter(self):
        now = 1795790000.0
        store.ingest_telemetry_batch({
            'install_id': 'inst-prod-01',
            'platform': 'android',
            'device_form': 'phone',
            'device_model': 'Samsung S26',
            'app_version': '2.19.2',
            'app_build': 277,
            'install_channel': 'apk',
            'events': [{'event_id': 'e101', 'type': 'session_start', 'session_id': 's101', 'section': 'comics', 'duration_sec': 45, 'ts': now}],
        }, now_ts=now)
        store.ingest_telemetry_batch({
            'install_id': 'inst-sim-01',
            'platform': 'ipados',
            'device_form': 'tablet',
            'device_model': 'iPad Simulator',
            'app_version': '2.19.2',
            'app_build': 277,
            'install_channel': 'simulator',
            'events': [{'event_id': 'e102', 'type': 'session_start', 'session_id': 's102', 'section': 'game', 'duration_sec': 120, 'ts': now}],
        }, now_ts=now)

        ok, _, _ = store.update_installation_meta('inst-prod-01', alias='Randy 主力机', notes='测试组长')
        self.assertTrue(ok)

        # Subsequent heartbeat must NOT wipe alias/notes
        store.ingest_telemetry_batch({
            'install_id': 'inst-prod-01',
            'platform': 'android',
            'app_version': '2.19.2',
            'app_build': 277,
            'install_channel': 'apk',
            'events': [{'event_id': 'e103', 'type': 'heartbeat', 'session_id': 's101', 'section': 'comics', 'duration_sec': 30, 'ts': now + 30}],
        }, now_ts=now + 30)

        prod_list = store.list_installations(include_sim=False, now_ts=now + 30)
        self.assertEqual(prod_list['total'], 1)
        self.assertEqual(prod_list['items'][0]['alias'], 'Randy 主力机')
        self.assertEqual(prod_list['items'][0]['notes'], '测试组长')
        self.assertEqual(prod_list['items'][0]['total_dwell_sec'], 75)

        all_list = store.list_installations(include_sim=True, now_ts=now + 30)
        self.assertEqual(all_list['total'], 2)

        ok_det, det, _ = store.get_installation_detail('inst-prod-01', now_ts=now + 30)
        self.assertTrue(ok_det)
        self.assertEqual(det['installation']['alias'], 'Randy 主力机')
        self.assertEqual(len(det['sessions']), 1)
        self.assertEqual(det['sessions'][0]['dwell_sec'], 75)

    def test_admin_auth_session_and_password_change(self):
        created, pwd = store.ensure_admin_auth_initialized(initial_password='InitialStrongPassword123!')
        self.assertTrue(created)
        self.assertTrue(store.verify_admin_credentials('admin', pwd))
        self.assertFalse(store.verify_admin_credentials('admin', 'wrong'))

        t1, _ = store.create_admin_session('admin', '127.0.0.1')
        t2, _ = store.create_admin_session('admin', '127.0.0.1')
        self.assertIsNotNone(store.verify_admin_session(t1))
        self.assertIsNotNone(store.verify_admin_session(t2))

        ok, _ = store.change_admin_password(pwd, 'NewStrongPassword456!', current_token=t1)
        self.assertTrue(ok)
        self.assertTrue(store.verify_admin_credentials('admin', 'NewStrongPassword456!'))
        self.assertIsNotNone(store.verify_admin_session(t1))
        self.assertIsNone(store.verify_admin_session(t2))

        store.revoke_admin_session(t1)
        self.assertIsNone(store.verify_admin_session(t1))

    def test_distribution_aggregation_and_csv_export(self):
        Path(store.DEVICES_FILE).write_text(json.dumps([
            {'udid': '00008140-0011223344556677', 'product': 'iPhone18,2', 'device_name': 'Randy iPhone', 'version': '27.2', 'apple_registered': True, 'registered_at': '2026-11-18 10:00:00', 'last_seen': '2026-11-20 10:00:00'}
        ]), encoding='utf-8')
        Path(store.PROVISIONED_UDIDS_FILE).write_text(json.dumps({
            'provisions_all_devices': False,
            'udids': ['00008140-0011223344556677'],
        }), encoding='utf-8')
        Path(store.INVITES_FILE).write_text(json.dumps([
            {'email': 'tester@example.com', 'status': 'added_to_group', 'message': 'ok', 'retry_count': 0, 'created_at': '2026-11-18 10:00:00', 'updated_at': '2026-11-18 10:05:00'}
        ]), encoding='utf-8')

        pb = sqlite3.connect(store.PB_DB_PATH)
        pb.execute('CREATE TABLE users (id TEXT PRIMARY KEY, email TEXT, name TEXT, verified INTEGER, created TEXT, updated TEXT)')
        pb.execute("INSERT INTO users VALUES ('u1', 'tester@example.com', 'Tester', 1, '2026-11-13', '2026-11-13')")
        pb.commit()
        pb.close()

        now = 1795790000.0
        store.ingest_telemetry_batch({
            'install_id': 'inst-acct-1',
            'platform': 'ios',
            'account_email': 'tester@example.com',
            'events': [{'event_id': 'ev-acct-1', 'type': 'session_start', 'session_id': 'sess-acct-1', 'section': 'forum', 'duration_sec': 88, 'ts': now}],
        }, now_ts=now)

        dist = store.get_distribution_summary(now_ts=now)
        self.assertEqual(len(dist['ota_devices']), 1)
        self.assertTrue(dist['ota_devices'][0]['provisioned_in_ipa'])
        self.assertEqual(len(dist['testflight_invites']), 1)
        self.assertEqual(len(dist['accounts']), 1)
        self.assertEqual(dist['accounts'][0]['device_count'], 1)
        self.assertEqual(dist['accounts'][0]['total_dwell_sec'], 88)

        csv_bytes = store.export_csv('all', True, now_ts=now)
        csv_text = csv_bytes.decode('utf-8-sig')
        self.assertIn('inst-acct-1', csv_text)
        self.assertIn('tester@example.com', csv_text)
        self.assertIn(',88,', csv_text)


if __name__ == '__main__':
    unittest.main()
