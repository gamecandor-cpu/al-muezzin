import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import net from "node:net";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = path.resolve(__dirname, "..");
const BUILD_APK_PATH = path.join(PROJECT_ROOT, "app", "build", "outputs", "apk", "debug", "app-debug.apk");
const ROOT_APK_PATH = path.join(PROJECT_ROOT, "al-muezzin.apk");
function resolveApkPath() {
  const candidates = [ROOT_APK_PATH, BUILD_APK_PATH].filter((p) => fs.existsSync(p));
  if (candidates.length === 0) return null;
  candidates.sort((a, b) => fs.statSync(b).mtimeMs - fs.statSync(a).mtimeMs);
  return candidates[0];
}
const PORT = Number(process.env.PORT || 5050);

const APP_LINK_META = {
  appName: "المؤذن | Muezzin",
  applicationId: "com.aistudio.almuezzin.qrwkpm",
  firebaseProjectId: "gen-lang-client-0088673365",
  firestoreDatabaseId: "ai-studio-android-almuezzi-66d87300-2690-43ed-b091-b03d839d2028",
  collection: "broadcast_reminders",
  fcmTopics: [
    "muezzin_announcements",
    "religious_occasions",
    "athkar_reminders",
    "broadcast_reminders"
  ]
};

const MIME_TYPES = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".ico": "image/x-icon",
  ".apk": "application/vnd.android.package-archive"
};

const AUDIO_EXT_LABELS = {
  ".mp3": "MP3",
  ".m4a": "M4A",
  ".aac": "AAC",
  ".ogg": "OGG",
  ".opus": "OPUS",
  ".wav": "WAV",
  ".flac": "FLAC",
  ".m3u8": "HLS",
  ".m3u": "M3U"
};

function sendJson(res, status, payload) {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "Access-Control-Allow-Origin": "*"
  });
  res.end(JSON.stringify(payload));
}

function isBlockedHostname(hostname) {
  const host = (hostname || "").toLowerCase();
  if (!host) return true;
  if (
    host === "localhost" ||
    host === "127.0.0.1" ||
    host === "0.0.0.0" ||
    host === "::1" ||
    host.endsWith(".local") ||
    host.endsWith(".internal")
  ) {
    return true;
  }
  const ipVer = net.isIP(host);
  if (ipVer === 4) {
    const parts = host.split(".").map(Number);
    if (
      parts[0] === 10 ||
      parts[0] === 127 ||
      parts[0] === 0 ||
      (parts[0] === 169 && parts[1] === 254) ||
      (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31) ||
      (parts[0] === 192 && parts[1] === 168)
    ) {
      return true;
    }
  } else if (ipVer === 6) {
    if (host === "::1" || host.startsWith("fe80:") || host.startsWith("fc") || host.startsWith("fd")) {
      return true;
    }
  }
  return false;
}

function validateExternalUrl(rawUrl) {
  let parsed;
  try {
    parsed = new URL((rawUrl || "").trim());
  } catch {
    return null;
  }
  if (parsed.protocol !== "https:" && parsed.protocol !== "http:") return null;
  if (isBlockedHostname(parsed.hostname)) return null;
  return parsed.href;
}

function toHttpsPreferred(rawUrl) {
  if (!rawUrl) return rawUrl;
  return rawUrl.startsWith("http://") ? "https://" + rawUrl.slice(7) : rawUrl;
}

function sniffAudioKind(bytes) {
  if (!bytes || bytes.length === 0) return null;
  const b = bytes;
  if (b.length >= 2 && b[0] === 0xff && (b[1] === 0xf1 || b[1] === 0xf9)) return "AAC";
  if (b.length >= 3 && b[0] === 0x49 && b[1] === 0x44 && b[2] === 0x33) return "MP3";
  if (b.length >= 2 && b[0] === 0xff && (b[1] & 0xe0) === 0xe0) return "MP3";
  if (b.length >= 12 && b[4] === 0x66 && b[5] === 0x74 && b[6] === 0x79 && b[7] === 0x70) {
    const brand = Buffer.from(b.slice(8, 12)).toString("ascii").toLowerCase();
    if (/m4a|m4b|mp4a|mp4|isom|qt/.test(brand)) return "M4A/AAC";
  }
  if (b.length >= 4 && b[0] === 0x4f && b[1] === 0x67 && b[2] === 0x67 && b[3] === 0x53) return "OGG/OPUS";
  if (
    b.length >= 12 &&
    b[0] === 0x52 && b[1] === 0x49 && b[2] === 0x46 && b[3] === 0x46 &&
    b[8] === 0x57 && b[9] === 0x41 && b[10] === 0x56 && b[11] === 0x45
  ) return "WAV";
  if (b.length >= 4 && b[0] === 0x66 && b[1] === 0x4c && b[2] === 0x61 && b[3] === 0x43) return "FLAC";
  if (b.length >= 4 && b[0] === 0x41 && b[1] === 0x44 && b[2] === 0x49 && b[3] === 0x46) return "AAC";
  const head = Buffer.from(b.slice(0, Math.min(256, b.length))).toString("ascii").trimStart();
  if (head.startsWith("#EXTM3U")) return "HLS";
  return null;
}

function contentTypeLooksAudio(ct) {
  const lower = (ct || "").toLowerCase();
  return (
    lower.includes("audio/") ||
    lower.includes("application/ogg") ||
    lower.includes("application/x-ogg") ||
    lower.includes("application/vnd.apple.mpegurl") ||
    lower.includes("application/x-mpegurl") ||
    lower.includes("audio/mpegurl")
  );
}

function contentTypeLooksHtmlOrText(ct) {
  const lower = (ct || "").toLowerCase();
  return (
    lower.includes("text/html") ||
    lower.includes("application/xhtml") ||
    lower.includes("application/xml") ||
    lower.includes("text/xml")
  );
}

function kindFromContentType(ct) {
  const lower = (ct || "").toLowerCase();
  if (lower.includes("mpegurl") || lower.includes("m3u8")) return "HLS";
  if (lower.includes("mp3") || lower.includes("mpeg")) return "MP3";
  if (lower.includes("m4a") || lower.includes("mp4") || lower.includes("aac")) return "M4A/AAC";
  if (lower.includes("ogg") || lower.includes("opus")) return "OGG/OPUS";
  if (lower.includes("wav") || lower.includes("wave")) return "WAV";
  if (lower.includes("flac")) return "FLAC";
  return "ملف صوتي";
}

function normalizeCloudUrl(rawUrl) {
  const trimmed = (rawUrl || "").trim();
  const lower = trimmed.toLowerCase();
  if (lower.includes("drive.google.com") || lower.includes("docs.google.com")) {
    const fileMatch = trimmed.match(/drive\.google\.com\/file\/d\/([a-zA-Z0-9_-]+)/);
    const idMatch = trimmed.match(/[?&]id=([a-zA-Z0-9_-]+)/);
    const fileId = (fileMatch && fileMatch[1]) || (idMatch && idMatch[1]);
    if (fileId) {
      return {
        directUrl: `https://drive.google.com/uc?export=download&id=${fileId}`,
        isCloud: true,
        provider: "Google Drive صوت"
      };
    }
  }
  if (lower.includes("dropbox.com")) {
    let direct = trimmed;
    if (lower.includes("dl=0")) direct = trimmed.replace("dl=0", "dl=1");
    else if (!lower.includes("dl=1")) direct = trimmed.includes("?") ? `${trimmed}&dl=1` : `${trimmed}?dl=1`;
    return {
      directUrl: direct,
      isCloud: true,
      provider: "Dropbox صوت"
    };
  }
  return { directUrl: trimmed, isCloud: false, provider: "" };
}

function extractTitleFromUrl(u) {
  try {
    const parsed = new URL(u);
    const seg = decodeURIComponent(parsed.pathname.split("/").pop() || "").replace(/\.[a-z0-9]{2,5}$/i, "");
    return seg.replace(/[_-]+/g, " ").trim() || parsed.hostname;
  } catch {
    return "مقطع صوتي";
  }
}

async function readFirstChunk(response, maxBytes = 8192) {
  if (!response.body) return new Uint8Array(0);
  const reader = response.body.getReader();
  const chunks = [];
  let total = 0;
  try {
    while (total < maxBytes) {
      const { value, done } = await reader.read();
      if (done || !value) break;
      chunks.push(value);
      total += value.length;
      if (total >= 2048) break;
    }
  } finally {
    try {
      await reader.cancel();
    } catch {
      // ignore
    }
  }
  const out = new Uint8Array(Math.min(total, maxBytes));
  let offset = 0;
  for (const chunk of chunks) {
    const copyLen = Math.min(chunk.length, out.length - offset);
    if (copyLen <= 0) break;
    out.set(chunk.subarray(0, copyLen), offset);
    offset += copyLen;
  }
  return out;
}

async function readLimitedText(response, maxBytes = 1500000) {
  if (!response.body) return "";
  const reader = response.body.getReader();
  const chunks = [];
  let total = 0;
  try {
    while (total < maxBytes) {
      const { value, done } = await reader.read();
      if (done || !value) break;
      chunks.push(value);
      total += value.length;
    }
  } finally {
    try {
      await reader.cancel();
    } catch {
      // ignore
    }
  }
  return Buffer.concat(chunks.map((c) => Buffer.from(c))).toString("utf8");
}

async function fetchSnippet(targetUrl, timeoutMs = 10000) {
  const valid = validateExternalUrl(targetUrl);
  if (!valid) throw new Error("رابط غير صالح أو محظور");

  const headers = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125.0 Mobile Safari/537.36",
    "Accept": "audio/*, text/html, application/xhtml+xml, application/xml, application/json, */*"
  };

  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    let res = await fetch(valid, {
      method: "GET",
      headers: { ...headers, Range: "bytes=0-8191" },
      redirect: "follow",
      signal: ctrl.signal
    });
    if ([400, 405, 416, 501].includes(res.status)) {
      try {
        await res.body?.cancel();
      } catch {
        // ignore
      }
      res = await fetch(valid, {
        method: "GET",
        headers,
        redirect: "follow",
        signal: ctrl.signal
      });
    }
    const contentType = res.headers.get("content-type") || "";
    const contentDisposition = res.headers.get("content-disposition") || "";
    const finalUrl = res.url || valid;

    if (contentTypeLooksHtmlOrText(contentType) || contentType.toLowerCase().includes("application/json")) {
      const text = await readLimitedText(res);
      return { ok: res.ok || res.status === 206, status: res.status, contentType, contentDisposition, finalUrl, bytes: new Uint8Array(0), text };
    }

    const bytes = await readFirstChunk(res, 8192);
    return { ok: res.ok || res.status === 206, status: res.status, contentType, contentDisposition, finalUrl, bytes, text: "" };
  } finally {
    clearTimeout(timer);
  }
}

function extractCandidatesFromHtml(htmlText, baseUrl) {
  const map = new Map();
  const pageTitleMatch = htmlText.match(/<title[^>]*>([\s\S]*?)<\/title>/i);
  const pageTitle = pageTitleMatch ? pageTitleMatch[1].replace(/\s+/g, " ").trim() : "";

  const add = (raw, hint = "") => {
    if (!raw || typeof raw !== "string") return;
    const cleaned = raw
      .trim()
      .replace(/\\u002F/gi, "/")
      .replace(/\\\//g, "/")
      .replace(/&amp;/g, "&")
      .replace(/^["']|["']$/g, "");
    if (!cleaned || cleaned.startsWith("data:") || cleaned.startsWith("blob:") || cleaned.startsWith("javascript:")) return;
    let absUrl;
    try {
      absUrl = new URL(cleaned, baseUrl).href;
    } catch {
      return;
    }
    const httpsUrl = toHttpsPreferred(absUrl);
    if (!validateExternalUrl(httpsUrl)) return;
    const cloud = normalizeCloudUrl(httpsUrl);
    const finalKey = cloud.directUrl;
    if (!map.has(finalKey)) {
      const extMatch = finalKey.split("?")[0].split("#")[0].match(/(\.[a-z0-9]{2,5})$/i);
      const extLabel = extMatch ? AUDIO_EXT_LABELS[extMatch[1].toLowerCase()] : "";
      map.set(finalKey, {
        url: finalKey,
        originalUrl: httpsUrl,
        title: (hint || extractTitleFromUrl(finalKey) || pageTitle).trim(),
        formatLabel: cloud.provider || extLabel || "صوت مكتشف"
      });
    }
  };

  // 1) <audio src="..."> and <audio> ... <source src="...">
  const audioBlockRegex = /<audio\b[^>]*>[\s\S]*?<\/audio>|<audio\b[^>]*>/gi;
  for (const block of htmlText.match(audioBlockRegex) || []) {
    const srcMatches = [...block.matchAll(/\b(?:src|data-src)\s*=\s*["']([^"']+)["']/gi)];
    const titleMatch = block.match(/\b(?:title|aria-label|data-title)\s*=\s*["']([^"']+)["']/i);
    for (const m of srcMatches) {
      add(m[1], titleMatch?.[1] || pageTitle);
    }
  }

  // 2) Standalone <source src="..." type="audio/..."> or <enclosure url="...">
  const sourceRegex = /<(?:source|enclosure|link)\b[^>]+>/gi;
  for (const tag of htmlText.match(sourceRegex) || []) {
    const urlMatch = tag.match(/\b(?:src|url|href|data-src)\s*=\s*["']([^"']+)["']/i);
    const typeMatch = tag.match(/\btype\s*=\s*["']([^"']+)["']/i);
    const relMatch = tag.match(/\brel\s*=\s*["']([^"']+)["']/i);
    if (!urlMatch) continue;
    const u = urlMatch[1];
    const t = (typeMatch?.[1] || "").toLowerCase();
    const r = (relMatch?.[1] || "").toLowerCase();
    if (
      t.startsWith("audio/") ||
      r.includes("enclosure") ||
      /\.(mp3|m4a|aac|ogg|opus|wav|flac|m3u8?)(\?|$|#)/i.test(u)
    ) {
      add(u, pageTitle);
    }
  }

  // 3) OpenGraph / Twitter audio meta tags
  const metaRegex = /<meta\b[^>]+>/gi;
  for (const tag of htmlText.match(metaRegex) || []) {
    if (/og:audio|twitter:player:stream/i.test(tag)) {
      const contentMatch = tag.match(/\bcontent\s*=\s*["']([^"']+)["']/i);
      if (contentMatch) add(contentMatch[1], pageTitle);
    }
  }

  // 4) Links and data attributes pointing to audio or cloud files
  const attrRegex = /\b(?:href|data-mp3|data-audio|data-stream|data-file|data-url|data-src)\s*=\s*["']([^"']+)["']/gi;
  for (const m of htmlText.matchAll(attrRegex)) {
    const val = m[1];
    if (
      /\.(mp3|m4a|aac|ogg|opus|wav|flac|m3u8?)(\?|$|#)/i.test(val) ||
      /drive\.google\.com\/file\/|dropbox\.com\//i.test(val)
    ) {
      add(val, "");
    }
  }

  // 5) JSON / JS properties like "audioUrl": "https://...", "mp3": "...", "contentUrl": "..."
  const jsonAudioKeyRegex = /"(?:audio|audioUrl|audio_url|mp3|stream|streamUrl|stream_url|contentUrl|file|soundUrl)"\s*:\s*"((?:https?:\\?\/\\?\/|\/)[^"]+)"/gi;
  for (const m of htmlText.matchAll(jsonAudioKeyRegex)) {
    add(m[1], pageTitle);
  }

  // 6) Raw URLs ending with audio extensions anywhere in HTML/scripts
  const rawAudioRegex = /https?:\\?\/\\?\/[^\s"'<>()\\]+\.(?:mp3|m4a|aac|ogg|opus|wav|flac|m3u8?)(?:\?[^\s"'<>()\\]*)?/gi;
  for (const m of htmlText.match(rawAudioRegex) || []) {
    add(m, "");
  }

  return Array.from(map.values());
}

async function probeCandidateTrack(track) {
  try {
    const cloud = normalizeCloudUrl(track.url);
    const probeUrl = cloud.directUrl;
    const snip = await fetchSnippet(probeUrl, 8000);
    const magic = sniffAudioKind(snip.bytes);
    const mimeOk = contentTypeLooksAudio(snip.contentType);

    if (magic || mimeOk) {
      return {
        ...track,
        url: toHttpsPreferred(snip.finalUrl || probeUrl),
        verified: true,
        formatLabel: magic || kindFromContentType(snip.contentType),
        contentType: snip.contentType,
        verifyNote: `تم التأكد فعلياً (${magic ? "بصمة الملف" : snip.contentType.split(";")[0]})`
      };
    }

    if (cloud.isCloud && snip.ok) {
      return {
        ...track,
        url: probeUrl,
        verified: true,
        formatLabel: cloud.provider,
        contentType: snip.contentType,
        verifyNote: "رابط سحابي قابل للوصول — تأكد أن المشاركة عامة"
      };
    }

    return {
      ...track,
      verified: false,
      contentType: snip.contentType,
      verifyNote: `الرابط لا يقدّم ملفاً صوتياً (${snip.contentType || snip.status})`
    };
  } catch (err) {
    return {
      ...track,
      verified: false,
      verifyNote: `تعذّر الفحص: ${err?.message || "خطأ شبكة"}`
    };
  }
}

async function handleAnalyzeUrl(rawUrl, res) {
  const valid = validateExternalUrl(rawUrl);
  if (!valid) {
    return sendJson(res, 400, { ok: false, error: "أدخل رابطاً خارجياً صحيحاً يبدأ بـ https:// أو http://" });
  }

  try {
    // Archive.org details/embed item API
    const archiveMatch = valid.match(/archive\.org\/(?:details|embed)\/([^/?#]+)/i);
    if (archiveMatch && archiveMatch[1]) {
      const itemId = archiveMatch[1];
      const metaSnip = await fetchSnippet(`https://archive.org/metadata/${encodeURIComponent(itemId)}`, 10000);
      if (metaSnip.text) {
        const metaJson = JSON.parse(metaSnip.text);
        const itemTitle = metaJson?.metadata?.title || itemId;
        const files = Array.isArray(metaJson?.files) ? metaJson.files : [];
        const rawTracks = files
          .filter((f) => /\.(mp3|m4a|ogg|wav|flac|aac|m3u8?)$/i.test(f.name || ""))
          .slice(0, 20)
          .map((f) => ({
            url: `https://archive.org/download/${encodeURIComponent(itemId)}/${encodeURIComponent(f.name).replace(/%2F/g, "/")}`,
            originalUrl: valid,
            title: f.title || f.name.replace(/\.[^.]+$/, ""),
            formatLabel: (f.name.split(".").pop() || "AUDIO").toUpperCase()
          }));
        if (rawTracks.length > 0) {
          const verified = await Promise.all(rawTracks.slice(0, 10).map(probeCandidateTrack));
          verified.sort((a, b) => Number(b.verified) - Number(a.verified));
          return sendJson(res, 200, {
            ok: true,
            mode: "webpage",
            pageTitle: typeof itemTitle === "string" ? itemTitle : itemId,
            tracks: verified
          });
        }
      }
    }

    const cloud = normalizeCloudUrl(valid);
    const targetToFetch = cloud.isCloud ? cloud.directUrl : valid;
    const snip = await fetchSnippet(targetToFetch, 11000);
    const magic = sniffAudioKind(snip.bytes);
    const mimeOk = contentTypeLooksAudio(snip.contentType);

    // Case 1: Direct audio link (even if URL has no .mp3 extension!)
    if (magic || mimeOk) {
      const finalHttps = toHttpsPreferred(snip.finalUrl || targetToFetch);
      const dispMatch = snip.contentDisposition.match(/filename\*?=(?:UTF-8'')?["']?([^"';]+)/i);
      const dispTitle = dispMatch ? decodeURIComponent(dispMatch[1]).replace(/\.[^.]+$/, "") : "";
      const formatLabel = magic || kindFromContentType(snip.contentType);
      return sendJson(res, 200, {
        ok: true,
        mode: "direct_audio",
        tracks: [
          {
            url: finalHttps,
            originalUrl: toHttpsPreferred(valid),
            title: dispTitle || extractTitleFromUrl(finalHttps),
            formatLabel,
            contentType: snip.contentType,
            verified: true,
            verifyNote: `تم التعرف تلقائياً على رابط صوت مباشر (${formatLabel})`
          }
        ]
      });
    }

    // Google Drive fallback if HTML confirm/viewer page was returned
    if (cloud.isCloud && snip.ok) {
      const driveConfirmMatch = snip.text.match(/href="([^"]*export=download[^"]*confirm=[^"]+)"/i) ||
        snip.text.match(/action="(https:\/\/drive\.usercontent\.google\.com\/download[^"]*)"/i);
      return sendJson(res, 200, {
        ok: true,
        mode: "direct_audio",
        tracks: [
          {
            url: toHttpsPreferred(driveConfirmMatch ? driveConfirmMatch[1].replace(/&amp;/g, "&") : cloud.directUrl),
            originalUrl: toHttpsPreferred(valid),
            title: cloud.provider,
            formatLabel: cloud.provider,
            contentType: snip.contentType,
            verified: true,
            verifyNote: "تم تجهيز رابط الصوت السحابي — تأكد أن الملف مشارك للعامة"
          }
        ]
      });
    }

    // Case 2: Webpage containing audio
    if (snip.text) {
      let candidates = extractCandidatesFromHtml(snip.text, snip.finalUrl || valid);

      // One-level iframe fallback if no direct audio tags found
      if (candidates.length === 0) {
        const iframeMatches = [...snip.text.matchAll(/<iframe\b[^>]*\bsrc\s*=\s*["']([^"']+)["']/gi)].slice(0, 3);
        for (const im of iframeMatches) {
          try {
            const iframeAbs = new URL(im[1], snip.finalUrl || valid).href;
            if (validateExternalUrl(iframeAbs)) {
              const iSnip = await fetchSnippet(iframeAbs, 7000);
              if (iSnip.text) {
                candidates = candidates.concat(extractCandidatesFromHtml(iSnip.text, iframeAbs));
              }
            }
          } catch {
            // ignore iframe errors
          }
        }
      }

      if (candidates.length === 0) {
        return sendJson(res, 200, {
          ok: false,
          mode: "webpage",
          error: "لم يُعثر على ملف صوتي مباشر داخل هذه الصفحة",
          tracks: []
        });
      }

      const verified = await Promise.all(candidates.slice(0, 12).map(probeCandidateTrack));
      verified.sort((a, b) => Number(b.verified) - Number(a.verified));
      return sendJson(res, 200, {
        ok: verified.some((t) => t.verified),
        mode: "webpage",
        tracks: verified
      });
    }

    return sendJson(res, 200, {
      ok: false,
      mode: "unknown",
      error: `الرابط لا يقدّم صوتاً أو صفحة تحتوي على صوت (status=${snip.status}, type=${snip.contentType || "unknown"})`,
      tracks: []
    });
  } catch (err) {
    return sendJson(res, 502, {
      ok: false,
      error: `تعذّر فحص الرابط: ${err?.message || "خطأ شبكة"}`,
      tracks: []
    });
  }
}

async function handleAudioProxy(rawUrl, req, res) {
  const valid = validateExternalUrl(rawUrl);
  if (!valid) {
    res.writeHead(400, { "Content-Type": "text/plain; charset=utf-8" });
    res.end("Invalid URL");
    return;
  }

  try {
    const upstreamHeaders = {
      "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125.0 Mobile Safari/537.36",
      "Accept": "audio/*, */*"
    };
    if (req.headers.range) {
      upstreamHeaders["Range"] = req.headers.range;
    }

    const upstream = await fetch(valid, {
      method: "GET",
      headers: upstreamHeaders,
      redirect: "follow"
    });

    const outHeaders = {
      "Access-Control-Allow-Origin": "*",
      "Cache-Control": "no-store"
    };
    const copyHeaders = ["content-type", "content-length", "content-range", "accept-ranges"];
    for (const h of copyHeaders) {
      const val = upstream.headers.get(h);
      if (val) outHeaders[h] = val;
    }
    if (!outHeaders["content-type"] || outHeaders["content-type"].includes("application/octet-stream")) {
      outHeaders["content-type"] = "audio/mpeg";
    }

    res.writeHead(upstream.status, outHeaders);
    if (!upstream.body) {
      res.end();
      return;
    }

    const reader = upstream.body.getReader();
    req.on("close", () => {
      try {
        reader.cancel();
      } catch {
        // ignore
      }
    });

    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      res.write(Buffer.from(value));
    }
    res.end();
  } catch (err) {
    if (!res.headersSent) {
      res.writeHead(502, { "Content-Type": "text/plain; charset=utf-8" });
    }
    res.end("Proxy audio stream failed: " + (err?.message || "error"));
  }
}

const server = http.createServer(async (req, res) => {
  const parsedReqUrl = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
  const urlPath = parsedReqUrl.pathname;

  if (urlPath === "/api/apk-info") {
    const apkPath = resolveApkPath();
    const exists = Boolean(apkPath);
    const stat = exists ? fs.statSync(apkPath) : null;
    return sendJson(res, 200, {
      ...APP_LINK_META,
      available: exists,
      fileName: "al-muezzin.apk",
      sizeBytes: stat ? stat.size : 0,
      sizeMb: stat ? (stat.size / (1024 * 1024)).toFixed(2) : "0",
      updatedAtIso: stat ? stat.mtime.toISOString() : null,
      downloadUrl: exists ? "/download/al-muezzin.apk" : null
    });
  }

  if (urlPath === "/download/al-muezzin.apk" || urlPath === "/al-muezzin.apk" || urlPath === "/app-debug.apk") {
    const apkPath = resolveApkPath();
    if (!apkPath) {
      res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
      res.end("APK not built yet. Run gradle assembleDebug first.");
      return;
    }
    const stat = fs.statSync(apkPath);
    res.writeHead(200, {
      "Content-Type": MIME_TYPES[".apk"],
      "Content-Length": stat.size,
      "Content-Disposition": 'attachment; filename="al-muezzin.apk"',
      "Cache-Control": "no-store"
    });
    fs.createReadStream(apkPath).pipe(res);
    return;
  }

  if (urlPath === "/api/analyze-url") {
    const target = parsedReqUrl.searchParams.get("url") || "";
    return handleAnalyzeUrl(target, res);
  }

  if (urlPath === "/api/audio-proxy") {
    const target = parsedReqUrl.searchParams.get("url") || "";
    return handleAudioProxy(target, req, res);
  }

  const safePath = urlPath === "/" ? "/index.html" : urlPath;
  let targetFile = path.normalize(path.join(__dirname, safePath));
  if (!targetFile.startsWith(__dirname) || !fs.existsSync(targetFile) || fs.statSync(targetFile).isDirectory()) {
    // Fallback to root index.html for single-page convenience
    const rootIndex = path.join(PROJECT_ROOT, "index.html");
    if (safePath === "/index.html" && fs.existsSync(rootIndex)) {
      targetFile = rootIndex;
    } else {
      res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
      res.end("Not Found");
      return;
    }
  }

  const ext = path.extname(targetFile).toLowerCase();
  res.writeHead(200, {
    "Content-Type": MIME_TYPES[ext] || "application/octet-stream",
    "Cache-Control": "no-store"
  });
  fs.createReadStream(targetFile).pipe(res);
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`Al-Muezzin Admin Portal running at http://localhost:${PORT}`);
});
