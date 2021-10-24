package com.example.exoplayer_sample

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.exoplayer_sample.databinding.ActivityMainBinding
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util

const val TAG = "MainActivityLog"

class MainActivity : AppCompatActivity() {

    private val viewBinding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private var player: SimpleExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
    }

    override fun onStart() {
        super.onStart()
        // Android APIレベル24以降では、マルチウィンドウをサポートしている。
        // 分割ウィンドウモードでは、アプリは表示されていてもアクティブではないため、onStart でPlayerを初期化する必要がある。
        if (Util.SDK_INT >= 24) {
            initializePlayer()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        // Android APIレベル24以下では、リソースを取得するまでできるだけ長く待つ必要があるため、onResumeまで待ってからプレーヤーを初期化する。
        if ((Util.SDK_INT < 24 || player == null)) {
            initializePlayer()
        }
    }

    override fun onPause() {
        super.onPause()
        // API Level 24以下では、onStopの呼び出しが保証されていないため、onPauseでできるだけ早くプレーヤーを解放する必要がある。
        if (Util.SDK_INT < 24) {
            releasePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        // マルチウィンドウや分割ウィンドウモードが導入されたAPI Level 24以降では、onStopが呼び出されることが保証されている。
        // 一時停止の状態ではアクティビティはまだ表示されているので、onStopまでプレーヤーの解放を待つ。
        if (Util.SDK_INT >= 24) {
            releasePlayer()
        }
    }

    private fun initializePlayer() {
        // Adaptive Streamingとは
        // 利用可能なネットワーク帯域幅に応じてストリームの品質を変化させ、メディアをストリーミングする技術。
        // これにより、ユーザーは帯域幅が許す限り、最高品質のメディアを体験することができる。
        // 通常、同じメディアコンテンツは、品質（ビットレートや解像度）の異なる複数のトラックに分割される。
        // プレーヤーは、利用可能なネットワーク帯域幅に応じてトラックを選択します。
        // 各トラックは、通常2〜10秒程度の一定時間のチャンクに分割されます。
        // これにより、プレイヤーは利用可能な帯域幅の変化に応じて、トラックを素早く切り替えることができる。
        // プレーヤーは、これらのチャンクをつなぎ合わせてシームレスに再生する役割を果たします。

        // まず、メディアアイテム内のトラックを選択する役割を担うDefaultTrackSelectorを作成する。
        // 次に、標準画質以下のトラックのみを選択するよう trackSelector に伝える。
        // これは、品質を犠牲にしてユーザーのデータを節約する良い方法。
        // 最後に、TrackSelectorをビルダーに渡し、SimpleExoPlayerのインスタンスを構築する際に使用されるようにする。
        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters().setMaxVideoSizeSd())
        }
        player = SimpleExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build()
            .also { exoPlayer ->
                viewBinding.videoView.player = exoPlayer

                // 再生コンテンツ
                val mediaItem = MediaItem.fromUri(getString(R.string.media_url_mp4))
                exoPlayer.setMediaItem(mediaItem)
                // 複数のMediaItemを追加するとPlaylist化される
                // https://medium.com/google-exoplayer/a-top-level-playlist-api-for-exoplayer-abe0a24edb55
                val mediaItem2nd = MediaItem.fromUri(getString(R.string.media_url_mp3))
                exoPlayer.addMediaItem(mediaItem2nd)

                // AdaptiveなMediaItemの構築
                // DASHは広く使用されているAdaptive Streaming形式。
                // https://en.wikipedia.org/wiki/Dynamic_Adaptive_Streaming_over_HTTP
                //
                // DASHコンテンツをストリーミングするには、fromUriではなくMediaItem.Builderを使用する必要がある。
                // これは、fromUriはファイル拡張子を使用して基礎となるメディアフォーマットを判断するが、
                // DASHのURI にはファイル拡張子がないため、MediaItemの構築時にAPPLICATION_MPDというMIMEタイプを指定する必要があるため。
                // ---
                // MediaItem.Builderでは、以下のような多数の追加プロパティを持つMediaItemsを作成できる。
                // - メディア コンテンツの MIME タイプ。
                // - DRM タイプ、ライセンス サーバーの URI、ライセンス要求ヘッダーなどの保護されたコンテンツのプロパティ。
                // - 再生時に使用するサイドロードされた字幕ファイル
                // - クリッピングの開始位置と終了位置
                // - 広告を挿入するための広告タグのURI。
                // https://exoplayer.dev/media-items.html
                val mediaItem3rd = MediaItem.Builder()
                    .setUri(getString(R.string.media_url_dash))
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .build()
                exoPlayer.addMediaItem(mediaItem3rd)

                // その他の Adaptive Streaming Format
                // HLS(MimeTypes.APPLICATION_M3U8)
                //   https://en.wikipedia.org/wiki/HTTP_Live_Streaming
                // SmoothStreaming(MimeTypes.APPLICATION_SS)
                //   https://en.wikipedia.org/wiki/Adaptive_bitrate_streaming#Microsoft_Smooth_Streaming
                // 上記2つは、他の一般的に使用されるアダプティブ・ストリーミング形式で、どちらもExoPlayerでサポートされている。


                // 状態の復元
                // 再生に必要なすべてのリソースを取得したら、すぐに再生を開始するかどうかをプレイヤーに伝える。
                // playWhenReadyの初期値はtrueなので、アプリを初めて起動したときに自動的に再生が開始される。
                exoPlayer.playWhenReady = _playWhenReady
                // seekToは、特定のウィンドウ内の特定の位置にシークするようプレーヤーに指示する。
                // currentWindowとplaybackPositionは0に初期化されているので、アプリを初めて起動したときに最初から再生が始まる
                exoPlayer.seekTo(_currentWindow, _playbackPosition)
                // 再生状態の変更を確認するためのリスナーを登録
                exoPlayer.addListener(playbackStateListener)
                // 再生に必要なすべてのリソースを取得することをプレーヤーに指示する。
                exoPlayer.prepare()
            }
    }

    @SuppressLint("InlinedApi")
    private fun hideSystemUi() {
        // TODO : systemUiVisibility is deprecated
        // Android11以降ではWindowInsetsControllerを使う
        // https://developer.android.com/reference/android/view/WindowInsetsController
        viewBinding.videoView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                )
    }

    private var _playWhenReady = true
    private var _currentWindow = 0
    private var _playbackPosition = 0L

    private fun releasePlayer() {
        player?.run {
            // playerを破棄する前に再生状態を保存する
            // https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/Timeline.html
            _playbackPosition = this.currentPosition
            // 現在の再生位置
            _currentWindow = this.currentWindowIndex
            // 再生 or 一時停止の状態
            _playWhenReady = this.playWhenReady
            removeListener(playbackStateListener)
            release()
        }
        player = null
    }

    private fun playbackStateListener() = object : Player.EventListener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            val state = when (playbackState) {
                // インスタンス化されているが、まだ準備されていない
                ExoPlayer.STATE_IDLE -> "Idle"
                // 十分なデータがbufferingされていないため、現在の位置から再生できない
                ExoPlayer.STATE_BUFFERING -> "Buffering"
                // 準備OK。現在の位置から再生可能。
                // playWhenReadyプロパティがtrueなら自動的に再生され、falseなら一時停止する
                ExoPlayer.STATE_READY -> "Ready"
                // メディアの再生を終えた
                ExoPlayer.STATE_ENDED -> "Ended"
                else -> "Unknown"
            }
            Log.d(TAG, "change state to $state")

            // 再生中か判断するにはExoPlayer.isPlaying
            // isPlayingが変更されたときに通知を受け取りたい場合はonIsPlayingChangedリスナが提供されている
        }
    }

    private val playbackStateListener: Player.EventListener = playbackStateListener()
}