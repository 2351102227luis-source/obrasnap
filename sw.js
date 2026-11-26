// ObraSnap service worker: la app abre sin señal en obra.
// index.html: red primero (para recibir actualizaciones), caché si no hay red.
// Fuentes y librerías del CDN: caché primero.
const CACHE='obrasnap-v21';
const CORE=['./','./index.html','./manifest.json','./icon-192.png','./icon-512.png','./icon-senal-192.png',
  './fonts/barlow-latin-400-normal.woff2','./fonts/barlow-latin-500-normal.woff2','./fonts/barlow-latin-600-normal.woff2','./fonts/barlow-latin-700-normal.woff2',
  './fonts/barlow-condensed-latin-500-normal.woff2','./fonts/barlow-condensed-latin-600-normal.woff2','./fonts/barlow-condensed-latin-700-normal.woff2','./fonts/share-tech-mono-latin-400-normal.woff2',
  './fonts/atkinson-hyperlegible-latin-400-normal.woff2','./fonts/atkinson-hyperlegible-latin-700-normal.woff2'];
self.addEventListener('install',e=>{
  e.waitUntil(caches.open(CACHE).then(c=>c.addAll(CORE)).then(()=>self.skipWaiting()));
});
self.addEventListener('activate',e=>{
  e.waitUntil(caches.keys().then(ks=>Promise.all(ks.filter(k=>k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim()));
});
self.addEventListener('fetch',e=>{
  const req=e.request;
  if(req.method!=='GET')return;
  const url=new URL(req.url);
  const sameOrigin=url.origin===self.location.origin;
  const isPage=req.mode==='navigate'||(sameOrigin&&/\/(index\.html)?$/.test(url.pathname));
  if(isPage){
    e.respondWith(fetch(req).then(r=>{const cp=r.clone();caches.open(CACHE).then(c=>c.put('./index.html',cp));return r;})
      .catch(()=>caches.match('./index.html')));
    return;
  }
  const cdn=/fonts\.(googleapis|gstatic)\.com$|cdnjs\.cloudflare\.com$/.test(url.hostname);
  if(sameOrigin||cdn){
    e.respondWith(caches.match(req).then(hit=>hit||fetch(req).then(r=>{
      if(r.ok||r.type==='opaque'){const cp=r.clone();caches.open(CACHE).then(c=>c.put(req,cp));}
      return r;
    })));
  }
});
