const PAGE = `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Fruit Factory V1</title><style>body{font-family:system-ui;background:#101218;color:#fff;max-width:700px;margin:40px auto;padding:24px}button,select,input{font-size:18px;padding:14px;margin:8px 0;width:100%;border-radius:12px;border:1px solid #333}.card{background:#1b1f2a;padding:24px;border-radius:20px}</style></head><body><div class="card"><h1>🍌 Fruit Factory V1</h1><p>Generate English + Arabic vertical shorts from GitHub Actions.</p><select id="language"><option value="both">English + Arabic</option><option value="en">English</option><option value="ar">العربية</option></select><select id="theme"><option value="surprise">Surprise</option><option value="funny">Funny</option></select><input id="key" type="password" placeholder="Dashboard key"><button onclick="go()">START / ابدأ</button><pre id="out"></pre></div><script>async function go(){out.textContent='Starting...';const r=await fetch('/api/start',{method:'POST',headers:{'content-type':'application/json','x-dashboard-key':key.value},body:JSON.stringify({language:language.value,theme:theme.value})});out.textContent=await r.text()}</script></body></html>`;

export default {
  async fetch(request, env) {
    const u = new URL(request.url);
    if (u.pathname === "/" && request.method === "GET") return new Response(PAGE,{headers:{"content-type":"text/html;charset=UTF-8"}});
    if (u.pathname === "/api/start" && request.method === "POST") {
      if (!env.DASHBOARD_KEY || request.headers.get("x-dashboard-key") !== env.DASHBOARD_KEY) return new Response("Unauthorized",{status:401});
      const body = await request.json();
      const r = await fetch(`https://api.github.com/repos/${env.GITHUB_OWNER}/${env.GITHUB_REPO}/actions/workflows/generate-short.yml/dispatches`,{
        method:"POST",
        headers:{"Authorization":`Bearer ${env.GITHUB_TOKEN}`,"Accept":"application/vnd.github+json","User-Agent":"fruit-factory-worker","Content-Type":"application/json"},
        body:JSON.stringify({ref:"main",inputs:{language:body.language||"both",theme:body.theme||"surprise",seed:""}})
      });
      return new Response(r.ok ? "Started ✅ / بدأ التوليد" : `GitHub error: ${r.status}`, {status:r.ok?200:500});
    }
    return new Response("Not found",{status:404});
  }
};
