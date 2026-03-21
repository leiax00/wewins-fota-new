// cloudflare/warm-worker.js
// 部署命令: npx wrangler deploy

export default {
    async fetch(request, env) {
        const DEFAULT_CACHE_TTL = 86400 * 30;

        if (request.method !== "POST") {
            return new Response("Method Not Allowed", { status: 405 });
        }

        const token = request.headers.get("X-Warm-Token");
        if (token !== env.WARM_SECRET) {
            return new Response("Unauthorized", { status: 401 });
        }

        const { firmware_url, ttl } = await request.json();
        if (!firmware_url) {
            return new Response("Missing firmware_url", { status: 400 });
        }

        const cacheTtl = normalizeCacheTtl(ttl, DEFAULT_CACHE_TTL);
        const result = await warmUrl(firmware_url, cacheTtl);
        return Response.json({ warmed: result.status > 0, url: firmware_url, ttl: cacheTtl, result });
    }
};

function normalizeCacheTtl(ttl, defaultTtl) {
    const parsed = Number(ttl);
    if (Number.isFinite(parsed) && parsed > 0) {
        return Math.floor(parsed);
    }
    return defaultTtl;
}

async function warmUrl(url, cacheTtl) {
    try {
        const resp = await fetch(url, {
            cf: {
                cacheEverything: true,
                cacheTtl,
            }
        });
        const cfRay = resp.headers.get("CF-RAY");
        return {
            status: resp.status,
            cache: resp.headers.get("CF-Cache-Status"),
            ray: cfRay,
            pop: extractPopFromCfRay(cfRay),
        };
    } catch (e) {
        return { status: 0, cache: "ERROR", error: e.message };
    }
}

function extractPopFromCfRay(cfRay) {
    if (!cfRay) {
        return null;
    }
    const index = cfRay.lastIndexOf("-");
    if (index < 0 || index === cfRay.length - 1) {
        return null;
    }
    return cfRay.slice(index + 1);
}
