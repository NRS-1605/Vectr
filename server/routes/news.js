const express = require("express");
const fs = require("fs/promises");
const Parser = require("rss-parser");
const { configPath } = require("../runtime-paths");

const CONFIG_PATH = configPath;
const CACHE_TTL_MS = 15 * 60 * 1000;
const parser = new Parser();
let cachedNews = null;
let cachedAt = 0;
let pendingFetch = null;

async function readConfig() {
  try {
    return JSON.parse(await fs.readFile(CONFIG_PATH, "utf8"));
  } catch (error) {
    if (error.code === "ENOENT") return {};
    throw error;
  }
}

async function readFeeds() {
  const config = await readConfig();
  return Array.isArray(config.feeds) ? config.feeds.filter((feed) => typeof feed === "string" && feed.trim()).map((feed) => feed.trim()) : [];
}

async function saveFeeds(feeds) {
  const config = await readConfig();
  const normalized = [...new Set(feeds.map((feed) => feed.trim()))];
  await fs.writeFile(CONFIG_PATH, `${JSON.stringify({ ...config, feeds: normalized }, null, 2)}\n`, "utf8");
  cachedNews = null;
  cachedAt = 0;
  return normalized;
}

function toNewsItem(feed, item, feedUrl) {
  const publishedAt = item.isoDate || item.pubDate || item.published || null;
  return {
    title: item.title || "Untitled",
    link: item.link || item.guid || feedUrl,
    source: feed.title || new URL(feedUrl).hostname,
    publishedAt,
  };
}

async function fetchNews() {
  if (cachedNews && Date.now() - cachedAt < CACHE_TTL_MS) return cachedNews;
  if (pendingFetch) return pendingFetch;
  pendingFetch = (async () => {
    const feeds = await readFeeds();
    const results = await Promise.allSettled(feeds.map(async (feedUrl) => {
      const feed = await parser.parseURL(feedUrl);
      return (feed.items || []).map((item) => toNewsItem(feed, item, feedUrl));
    }));
    const news = results.flatMap((result) => result.status === "fulfilled" ? result.value : []);
    news.sort((a, b) => (Date.parse(b.publishedAt) || 0) - (Date.parse(a.publishedAt) || 0));
    cachedNews = news;
    cachedAt = Date.now();
    return news;
  })();
  try {
    return await pendingFetch;
  } finally {
    pendingFetch = null;
  }
}

function createNewsRoutes() {
  const router = express.Router();

  router.get("/news/feeds", async (req, res, next) => {
    try { res.json(await readFeeds()); } catch (error) { next(error); }
  });

  router.post("/news/feeds", async (req, res, next) => {
    const feeds = req.body?.feeds;
    if (!Array.isArray(feeds) || feeds.some((feed) => typeof feed !== "string" || !feed.trim())) {
      return res.status(400).json({ error: "feeds must be an array of non-empty URLs." });
    }
    try {
      const invalidFeed = feeds.find((feed) => { try { const url = new URL(feed.trim()); return !["http:", "https:"].includes(url.protocol); } catch (_) { return true; } });
      if (invalidFeed) return res.status(400).json({ error: `Invalid feed URL: ${invalidFeed}` });
      return res.json(await saveFeeds(feeds));
    } catch (error) { return next(error); }
  });

  router.get("/news", async (req, res, next) => {
    try { res.json(await fetchNews()); } catch (error) { next(error); }
  });
  return router;
}

module.exports = { createNewsRoutes, fetchNews, readFeeds };
