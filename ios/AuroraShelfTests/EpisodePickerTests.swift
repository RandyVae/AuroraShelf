import XCTest
@testable import AuroraShelf

@MainActor
final class EpisodePickerTests: XCTestCase {
    
    func testPlaybackEpisodeModel() {
        let ep1 = PlaybackEpisode(number: 1, label: "第 1 集", pageUrl: "https://huangguoai.com/video/1/ep-1/")
        let ep2 = PlaybackEpisode(number: 2, label: "第 2 集", pageUrl: "https://huangguoai.com/video/1/ep-2/")
        let ep10 = PlaybackEpisode(number: 10, label: "第 10 集", pageUrl: "https://huangguoai.com/video/1/ep-10/")
        
        XCTAssertEqual(ep1.id, "1-https://huangguoai.com/video/1/ep-1/")
        XCTAssertEqual(ep2.id, "2-https://huangguoai.com/video/1/ep-2/")
        XCTAssertEqual(ep1.number, 1)
        XCTAssertEqual(ep1.label, "第 1 集")
        
        let unsorted = [ep10, ep1, ep2]
        let sorted = unsorted.sorted(by: { $0.number < $1.number })
        XCTAssertEqual(sorted.map(\.number), [1, 2, 10])
    }

    func testResolveEpisodesForSingleSource() async {
        let video = VideoItem(
            id: "porn91-12345",
            title: "91特选视频",
            subtitle: "高清原画",
            coverUrl: "https://example.com/cover.jpg",
            playbackUrl: "https://91porn.com/view_video.php?viewkey=abcde12345",
            duration: "15:20",
            rating: "9.8",
            tags: ["热门"],
            sourceName: "91视频"
        )
        
        let episodes = await VideoRepository.shared.resolveEpisodes(for: video)
        XCTAssertEqual(episodes.count, 1)
        XCTAssertEqual(episodes.first?.label, "正片")
        XCTAssertEqual(episodes.first?.number, 1)
        XCTAssertEqual(episodes.first?.pageUrl, video.playbackUrl)
    }

    func testEpisodeVideoItemConstruction() {
        let baseVideo = VideoItem(
            id: "hg-999",
            title: "霸道总裁爱上我",
            subtitle: "全网热播短剧",
            coverUrl: "https://example.com/poster.jpg",
            playbackUrl: "https://huangguoai.com/video/999/ep-1/",
            duration: "02:00",
            rating: "9.9",
            tags: ["都市", "热血"],
            sourceName: "黄果短剧"
        )
        
        let ep3 = PlaybackEpisode(number: 3, label: "第 3 集", pageUrl: "https://huangguoai.com/video/999/ep-3/")
        
        let epVideo = VideoItem(
            id: "\(baseVideo.id)-ep-\(ep3.number)",
            title: "\(baseVideo.title) \(ep3.label)",
            subtitle: baseVideo.subtitle,
            coverUrl: baseVideo.coverUrl,
            playbackUrl: ep3.pageUrl,
            duration: baseVideo.duration,
            rating: baseVideo.rating,
            tags: baseVideo.tags,
            sourceName: baseVideo.sourceName
        )
        
        XCTAssertEqual(epVideo.id, "hg-999-ep-3")
        XCTAssertEqual(epVideo.title, "霸道总裁爱上我 第 3 集")
        XCTAssertEqual(epVideo.playbackUrl, "https://huangguoai.com/video/999/ep-3/")
        XCTAssertEqual(epVideo.sourceName, "黄果短剧")
    }

    func testParseHuavodEpisodesFromPlayPageWithDataUrl() {
        let samplePlayHtml = """
        <ul class="anthology-list-play">
            <li class="anthology-item"><a class="episode-link" href="javascript:void(0)" data-url="/vodplay/132170-1-1.html"><span>第1集</span><em class="play-on"><i></i><i></i></em></a></li>
            <li class="anthology-item"><a class="episode-link" href="javascript:void(0)" data-url="/vodplay/132170-1-2.html"><span>第2集</span><em class="play-on"><i></i><i></i></em></a></li>
            <li class="anthology-item"><a class="episode-link" href="javascript:void(0)" data-url="/vodplay/132170-1-3.html"><span>第3集</span><em class="play-on"><i></i><i></i></em></a></li>
        </ul>
        <ul class="anthology-list-play">
            <li class="anthology-item"><a class="episode-link" href="javascript:void(0)" data-url="/vodplay/132170-2-1.html"><span>第1集</span></a></li>
        </ul>
        """

        let episodes = VideoRepository.shared.parseHuavodEpisodesFromHtml(samplePlayHtml)
        XCTAssertEqual(episodes.count, 3)
        XCTAssertEqual(episodes[0].number, 1)
        XCTAssertEqual(episodes[0].label, "第1集")
        XCTAssertEqual(episodes[0].pageUrl, "https://huavod.com/vodplay/132170-1-1.html")
        XCTAssertEqual(episodes[2].number, 3)
        XCTAssertEqual(episodes[2].label, "第3集")
        XCTAssertEqual(episodes[2].pageUrl, "https://huavod.com/vodplay/132170-1-3.html")
    }

    func testParseHuavodEpisodesFromDetailPageWithHref() {
        let sampleDetailHtml = """
        <div class="anthology-list-play">
            <a href="/vodplay/204306-1-1.html">精编回顾</a>
            <a href="/vodplay/204306-1-2.html">20261010(第1期)</a>
            <a href="/vodplay/204306-1-3.html">20261011(第2期)</a>
        </div>
        """

        let episodes = VideoRepository.shared.parseHuavodEpisodesFromHtml(sampleDetailHtml)
        XCTAssertEqual(episodes.count, 3)
        XCTAssertEqual(episodes[0].number, 1)
        XCTAssertEqual(episodes[0].label, "精编回顾")
        XCTAssertEqual(episodes[0].pageUrl, "https://huavod.com/vodplay/204306-1-1.html")
        XCTAssertEqual(episodes[1].number, 2)
        XCTAssertEqual(episodes[1].label, "20261010(第1期)")
    }
}
