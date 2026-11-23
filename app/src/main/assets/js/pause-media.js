/* Pauses every video and audio of the page when the app goes to the background. */
document.querySelectorAll('video,audio').forEach(function(m){m.pause()})
