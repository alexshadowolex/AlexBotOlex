window.onload = () => {
    const webSocket = new WebSocket(`ws://localhost:${serverPort}/socket`);
    const videoPlayer = document.querySelector('#video-player');
    const warning = document.querySelector('#warning');

    let lastRequestTimestamp = Date.now();

    // On certain video files, for whatever reason, Chromium does not call the `ended` event at the end of the clip but only the `pause` event.
    // This "fix" will cause manually requested pause events (e.g. by using the video player controls) to not work.
    videoPlayer.onended = videoPlayer.onpause = () => {
        // In Vanilla OBS for some reason the <video> element fires an pause/ended event after each clip that is not present
        // in Chromium, Firefox or Streamlabs OBS; which results in 2 clips getting requested and only one being shown.
        // This is fixed by checking if the a request got issued within a certain time interval (100ms) of another one and if yes ignoring it.
        // Note that this fix might get the player stuck for 10s if a clip with length <= 100ms was played.
        if (Date.now() - lastRequestTimestamp < 100) {
            console.log('Duplicating video end event, ignoring...');

            window.setTimeout(() => {
                if (videoPlayer.paused || videoPlayer.ended) {
                    console.log('Video player got stuck, requesting next clip...');
                    webSocket.send('next video rage emoji');
                }
            }, 10_000);

            return;
        }

        console.log('Video done, requesting next one...');
        webSocket.send('next video lol');

        lastRequestTimestamp = Date.now();
    };

    webSocket.onopen = () => {
        warning.style.visibility = 'hidden';
        webSocket.send('next video lol');
    };

    webSocket.onmessage = message => {
        console.log(`Received next video "${message.data}".`);
        videoPlayer.src = `/video/${message.data}`;
    };

    webSocket.onclose = webSocket.onerror = e => {
        console.error('WebSocket was closed:', e);
        warning.style.visibility = 'visible';
    };
};