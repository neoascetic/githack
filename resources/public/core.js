var term = new Terminal({cols: 80, rows: 24, screenKeys: true});
term.open(document.body);
if (window.ttyrecUrl) {
    var player = new TTYPlay(term, { url: window.ttyrecUrl, speed: 0.001 });
} else {
    term.writeln('Welcome to GitHack! Full moon tonight.')
    term.writeln('Please stay tight, your adventure starts very soon...')
    setTimeout(function () { window.location.reload(); }, 5000);
}
