import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 5050);

const MIME_TYPES = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".ico": "image/x-icon"
};

const server = http.createServer((req, res) => {
  const urlPath = (req.url || "/").split("?")[0];
  const safePath = urlPath === "/" ? "/index.html" : urlPath;
  const targetFile = path.normalize(path.join(__dirname, safePath));

  if (!targetFile.startsWith(__dirname) || !fs.existsSync(targetFile) || fs.statSync(targetFile).isDirectory()) {
    res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
    res.end("Not Found");
    return;
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
