/*
 * Hides the pointer when a video starts with sound. Muted videos are usually decoration (hero loops, hover
 * previews) and must not take the pointer away while the user navigates. Sees only the main frame, not
 * videos inside iframes.
 */
if(!window.__tvwVideo){window.__tvwVideo=1;document.addEventListener('playing',function(e){var v=e.target;
if(v.tagName==='VIDEO'&&!v.muted&&v.volume>0)tvwebapp.started()},true)}
