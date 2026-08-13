"use strict";
/**
 * Extracts the normalized Gate API v4 protocol surface from the official
 * server-rendered docs pages (www.gate.com/docs/developers/apiv4/en/<domain>/).
 *
 * Dev-time tool: the docs site is protected by Akamai and rejects plain
 * curl/fetch; it accepts requests from a real Chromium page context. Run the
 * per-domain extraction inside the browser tool's Node context (which has
 * network + fs access), POSTing each normalized domain payload to a local
 * collector; then run assemble-gate-fixture.py and check-gate-api-drift.py.
 *
 *   node-context snippet:
 *     const mod = require('<abs path>/extract-gate-docs.js');
 *     const out = await mod.extractDomain('spot');
 *     await fetch('http://127.0.0.1:8811/spot',
 *       {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify(out)});
 */
const fs = require("fs");
const path = require("path");

function decodeEntities(s) {
  return s
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&amp;/g, "&")
    .replace(/&nbsp;/g, " ")
    .replace(/&#x(\d+);/g, (m, h) => String.fromCodePoint(parseInt(h, 16)))
    .replace(/&#(\d+);/g, (m, d) => String.fromCodePoint(parseInt(d, 10)));
}

function stripTags(s) {
  return decodeEntities(s.replace(/<[^>]*>/g, " ")).replace(/\s+/g, " ").trim();
}

function parseTable(html) {
  // returns array of row objects using first row as header
  const rows = [];
  const rowRe = /<tr>(.*?)<\/tr>/gs;
  let m;
  while ((m = rowRe.exec(html)) !== null) {
    const cells = [...m[1].matchAll(/<t[dh][^>]*>(.*?)<\/t[dh]>/gs)].map((c) =>
      stripTags(c[1])
    );
    if (cells.length) rows.push(cells);
  }
  if (!rows.length) return [];
  const header = rows[0];
  return rows.slice(1).map((r) => {
    const o = {};
    header.forEach((h, i) => {
      o[h] = r[i] !== undefined ? r[i] : "";
    });
    return o;
  });
}

function extractEndpoints(html) {
  const endpoints = [];
  // endpoint marker: <p><code>GET /path</code></p>
  const markerRe = /<p><code>\s*(GET|POST|DELETE|PUT|PATCH)\s+([^<]+?)<\/code><\/p>/g;
  const parts = [];
  let m;
  let last = 0;
  while ((m = markerRe.exec(html)) !== null) {
    parts.push({ method: m[1], path: m[2].trim(), start: m.index, end: markerRe.lastIndex });
  }
  for (let i = 0; i < parts.length; i++) {
    const p = parts[i];
    const blockEnd = i + 1 < parts.length ? parts[i + 1].start : html.length;
    const block = html.slice(p.end, blockEnd);
    const titleM = block.match(/<p><em>(.*?)<\/em><\/p>/);
    const params = [];
    const paramM = block.match(/<h3[^>]*>Parameters<\/h3>\s*<table>(.*?)<\/table>/s);
    if (paramM) params.push(...parseTable(paramM[1]));
    const responses = [];
    const respM = block.match(/<h3[^>]*>Responses<\/h3>\s*<table>(.*?)<\/table>/s);
    if (respM) responses.push(...parseTable(respM[1]));
    const schemas = [];
    const schemaRe = /<p>Status Code <strong>(\d+)<\/strong><\/p>\s*<div[^>]*schema-block[^>]*>\s*<table>(.*?)<\/table>/gs;
    let sm;
    while ((sm = schemaRe.exec(block)) !== null) {
      schemas.push({ status: sm[1], fields: parseTable(sm[2]) });
    }
    const requiresAuth = !block.includes('does not require authentication');
    endpoints.push({
      method: p.method,
      path: p.path,
      summary: titleM ? stripTags(titleM[1]) : "",
      requiresAuth,
      params,
      responses,
      schemas,
    });
  }
  return endpoints;
}

async function extractDomain(domain) {
  const url = `https://www.gate.com/docs/developers/apiv4/en/${domain}/`;
  const r = await fetch(url, { headers: { Accept: "text/html" } });
  const html = await r.text();
  if (r.status !== 200) throw new Error(`HTTP ${r.status} for ${url}`);
  const endpoints = extractEndpoints(html);
  return {
    domain,
    source: url,
    fetchedAt: new Date().toISOString(),
    endpointCount: endpoints.length,
    endpoints,
  };
}

module.exports = { extractDomain, extractEndpoints };
if (require.main === module) {
  const domain = process.argv[2];
  if (!domain) {
    console.error("usage: node extract.js <domain>");
    process.exit(1);
  }
  extractDomain(domain)
    .then((out) => {
      console.log(JSON.stringify({ ok: true, domain, count: out.endpointCount }));
    })
    .catch((e) => {
      console.error(String(e));
      process.exit(1);
    });
}
