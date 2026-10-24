import SwiftUI
import MediaPlayer

/// Now playing from the system music player (Apple Music / local library).
/// iOS does not let apps read other apps' playback (Spotify, YouTube Music, ...).
final class NowPlayingMonitor: ObservableObject {
    struct Track: Equatable { var title: String; var artist: String; var art: UIImage? }

    @Published private(set) var track: Track?
    private let player = MPMusicPlayerController.systemMusicPlayer

    func start() {
        MPMediaLibrary.requestAuthorization { [weak self] status in
            guard status == .authorized else { return }
            DispatchQueue.main.async { self?.observe() }
        }
    }

    private func observe() {
        player.beginGeneratingPlaybackNotifications()
        NotificationCenter.default.addObserver(
            self, selector: #selector(refresh), name: .MPMusicPlayerControllerNowPlayingItemDidChange, object: player)
        refresh()
    }

    @objc private func refresh() {
        guard let item = player.nowPlayingItem, let title = item.title, !title.isEmpty else {
            track = nil
            return
        }
        track = Track(title: title, artist: item.artist ?? "",
                      art: item.artwork?.image(at: CGSize(width: 160, height: 160)))
    }
}

struct NowPlayingBar: View {
    @ObservedObject var monitor: NowPlayingMonitor
    let fg: Color
    var compact = false

    var body: some View {
        if let t = monitor.track {
            HStack(spacing: compact ? 8 : 12) {
                if let art = t.art {
                    Image(uiImage: art)
                        .resizable()
                        .grayscale(1)
                        .opacity(0.8)
                        .frame(width: compact ? 40 : 56, height: compact ? 40 : 56)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(t.title).font(.system(size: compact ? 13 : 16)).foregroundColor(fg).lineLimit(1)
                    if !t.artist.isEmpty {
                        Text(t.artist).font(.system(size: compact ? 11 : 14)).foregroundColor(fg.opacity(0.7)).lineLimit(1)
                    }
                }
            }
            .padding(compact ? 0 : 16)
            .frame(maxWidth: compact ? 170 : .infinity, alignment: compact ? .leading : .center)
        }
    }
}
