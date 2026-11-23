/*
 * Defines window.__tvw in the page (once per document). The app replaces @ACCENT@ and @OUTLINE@ with the
 * theme colours and appends `;__tvw.ring(on)`. Bridge: window.tvwebapp (PageBridge in WebClients.kt).
 *
 * ring(on): draws a frame around the focused element, also where the site hides its own focus outline;
 *   follows the element every frame (scrolling, animations); main frame only, a focused iframe gets the
 *   frame as a whole.
 * focusCenter(): centre of the focused element in device pixels as [x, y], or null.
 * focusAt(x, y): focuses the focusable element at device pixels x, y, if there is one.
 * Reports to the app (as PageFocus ordinal: 0 none, 1 iframe, 2 video, 3 text) what the focus is on outside
 * fullscreen (every frame while the ring is on, otherwise on focus changes), and what is in fullscreen.
 * Player page: a video fills half the screen and at most PAGE_EXTRAS focusable elements lie outside its
 * player element (a hoster's embed page; a site with a big trailer has many links, so it is none).
 * Rechecked at most once a second. There the player element counts as a video, and while the ring is on
 * and nothing has the focus, it gets the focus, so OK plays it right away.
 * For a focused player:
 *   standIn(down, up) moves the focus onto a stand-in over the player in the main frame, so the next
 *   arrow key navigates from there; for down/up it first scrolls the player's far edge to mid-screen,
 *   so the elements beyond a tall player are in view. Returns false if no player has the focus.
 *   enterPlayer() plays it in fullscreen (needs a user gesture just before).
 * For a video player in fullscreen: seek(seconds), toggle() play/pause; act on its largest video.
 * Text fields reached in focus mode get inputmode=none, so no keyboard pops up while moving through
 * the page; allowKeyboard() restores it on the focused field (the app then sends OK, which opens the
 * keyboard), leaving focus mode restores it too.
 * The ring hides while a player is in fullscreen. An iframe in fullscreen gets the focus back (the
 * page loses it on the switch), so the keys reach the embedded player. Leaving fullscreen scrolls the
 * focused element back into view (entering it scrolls the page to the top).
 * The frame element sits outside body and cancels the page scale of the zoom option (zoom-viewport.js).
 */
if(!window.__tvw)window.__tvw=(function(){
var box=document.createElement('tvw-focus'),on=false,running=false,last='',focused=0;
var SEL='a[href],button,input,select,textarea,summary,[tabindex],[contenteditable]',PAGE_EXTRAS=5;
var BASE='position:fixed;pointer-events:none;z-index:2147483647;box-sizing:border-box;margin:0;padding:0;'+
'border:3px solid @ACCENT@;border-radius:6px;box-shadow:0 0 0 2px @OUTLINE@,inset 0 0 0 2px @OUTLINE@;background:none;display:block;';
function active(){var e=document.activeElement;while(e&&e.shadowRoot&&e.shadowRoot.activeElement)e=e.shadowRoot.activeElement;
return e&&e!==document.body&&e!==document.documentElement?e:null}
function scale(){return screen.width/document.documentElement.clientWidth||1}
function px(){return devicePixelRatio*scale()}
function area(v){var r=v.getBoundingClientRect();return r.width*r.height}
function largest(root){var best=null,a=0;root.querySelectorAll('video').forEach(function(v){var x=area(v);if(x>a){a=x;best=v}});return best}
var pp=null,ppAt=0;
function playerEl(){var t=Date.now();if(t-ppAt<1000)return pp;ppAt=t;pp=null;var v=largest(document);
if(!v||area(v)<innerWidth*innerHeight/2)return null;var p=v.parentElement&&v.parentElement.closest(SEL);
if(!p||/^(A|BUTTON)$/.test(p.tagName)||area(p)>area(v)*1.5)p=v;var n=0;
document.querySelectorAll(SEL).forEach(function(x){if(!p.contains(x)&&x.getClientRects().length)n++});
if(n<=PAGE_EXTRAS)pp=p;return pp}
function kind(e){return !e?0:e.tagName==='IFRAME'?1:e.tagName==='VIDEO'||e===playerEl()?2:editable(e)?3:0}
function autoFocus(){var p=playerEl();if(!p)return;if(!p.matches(SEL))p.tabIndex=-1;p.focus({preventScroll:true})}
function player(e){var k=kind(e);return k===1||k===2}
function fsKind(f){return !f?0:f.tagName==='IFRAME'?1:(f.tagName==='VIDEO'||f.querySelector('video'))?2:0}
function report(k){if(k!==focused){focused=k;tvwebapp.focused(k)}}
document.addEventListener('fullscreenchange',function(){var f=document.fullscreenElement;tvwebapp.fullscreen(fsKind(f));
if(f){if(f.tagName==='IFRAME')f.focus()}else requestAnimationFrame(function(){var e=active();if(e)e.scrollIntoView({block:'center'})})});
function vid(){var f=document.fullscreenElement,best=null,a=0;if(!f)return null;if(f.tagName==='VIDEO')return f;
f.querySelectorAll('video').forEach(function(v){var r=v.getBoundingClientRect();if(r.width*r.height>=a){a=r.width*r.height;best=v}});return best}
function quiet(p){if(p&&p.catch)p.catch(function(){})}
function editable(e){return !!e&&(e.isContentEditable||e.tagName==='TEXTAREA'||e.tagName==='INPUT'&&
/^(text|search|email|url|tel|password|number)$/.test(e.type))}
var allowed=null;
function restore(e){if(!e||!e.hasAttribute||!e.hasAttribute('data-tvw-im'))return;var m=e.getAttribute('data-tvw-im');
e.removeAttribute('data-tvw-im');if(m)e.setAttribute('inputmode',m);else e.removeAttribute('inputmode')}
function block(e){if(!on||e===allowed||!editable(e)||e.hasAttribute('data-tvw-im'))return;
e.setAttribute('data-tvw-im',e.getAttribute('inputmode')||'');e.setAttribute('inputmode','none')}
document.addEventListener('focusin',function(ev){block(ev.target)},true);
document.addEventListener('focusout',function(ev){if(ev.target===allowed)allowed=null;restore(ev.target)},true);
function now(){return document.fullscreenElement?0:kind(active())}
// Without the ring: focus moving into an iframe only blurs this window. Reported at once, before the
// click that moved it can open a popup; again later in case the focus was not settled yet.
function later(){if(!on)setTimeout(function(){report(now())})}
addEventListener('blur',function(){if(!on)report(now());later()});addEventListener('focus',later);document.addEventListener('focusin',later,true);
function tick(){if(!on){running=false;box.remove();last='';report(now());return}requestAnimationFrame(tick);
var fs=document.fullscreenElement;if(!fs&&!active())autoFocus();
var e=active(),s='display:none;';report(fs?0:kind(e));if(fsKind(fs))e=null;
if(e){var r=e.getBoundingClientRect(),w=innerWidth,h=innerHeight,
l=Math.max(2,r.left-4),t=Math.max(2,r.top-4),R=Math.min(w-2,r.right+4),B=Math.min(h-2,r.bottom+4);
var q=scale();if((r.width||r.height)&&R>l&&B>t)s='left:'+l*q+'px;top:'+t*q+'px;width:'+(R-l)*q+'px;height:'+(B-t)*q+'px;';}
var host=fs&&fs.tagName!=='VIDEO'&&fs.tagName!=='IFRAME'?fs:document.documentElement;
if(box.parentNode!==host)host.appendChild(box);
s+='zoom:'+(1/scale());if(s!==last){last=s;box.style.cssText=BASE+s}}
return{ring:function(v){on=v;if(on)block(active());else restore(active());if(on&&!running){running=true;tick()}},
allowKeyboard:function(){var e=active();allowed=e;restore(e)},
focusCenter:function(){var e=active();if(!e)return null;var r=e.getBoundingClientRect(),d=px();
return[(r.left+r.width/2)*d,(r.top+r.height/2)*d]},
focusAt:function(x,y){var d=px(),e=document.elementFromPoint(x/d,y/d);e=e&&e.closest(SEL);
if(e)e.focus({preventScroll:true})},
enterPlayer:function(){var e=active();if(!player(e))return;var v=e.tagName==='VIDEO'?e:e===playerEl()?largest(e):null;if(v)quiet(v.play());
quiet(e.requestFullscreen())},
seek:function(s){var v=vid();if(v)v.currentTime=Math.max(0,Math.min(v.duration||1e9,v.currentTime+s))},
toggle:function(){var v=vid();if(v){if(v.paused)quiet(v.play());else v.pause()}},
standIn:function(down,up){var f=active();if(!player(f)){report(0);return false}
var r=f.getBoundingClientRect(),h=innerHeight;
if(down&&r.bottom>h-40)scrollBy({top:r.bottom-h/2,behavior:'instant'});
if(up&&r.top<40)scrollBy({top:r.top-h/2,behavior:'instant'});
r=f.getBoundingClientRect();var o=document.documentElement.getBoundingClientRect(),p=document.createElement('tvw-standin');p.tabIndex=-1;
p.style.cssText='position:absolute;display:block;outline:none;pointer-events:none;left:'+(r.left-o.left)+'px;top:'+(r.top-o.top)+
'px;width:'+r.width+'px;height:'+r.height+'px';
p.addEventListener('blur',function(){p.remove()});document.documentElement.appendChild(p);
p.focus({preventScroll:true});report(0);return true}}})()
