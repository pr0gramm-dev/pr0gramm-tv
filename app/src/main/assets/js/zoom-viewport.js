/*
 * Zoom option: gives the page a viewport of screen width * 100 / zoom CSS pixels, shown at zoom / 100,
 * like browser zoom: the page lays out for that width (its media queries see it), e.g. 50 -> 1920.
 * A function expression; the app appends `(zoom)`.
 */
(function(z){var m=document.querySelector('meta[data-tvw]');
if(!m){m=document.createElement('meta');m.name='viewport';m.setAttribute('data-tvw','');}
(document.head||document.documentElement).appendChild(m);
m.content='width='+Math.round(screen.width*100/z)+',initial-scale='+z/100+',minimum-scale='+z/100+',maximum-scale='+z/100})
