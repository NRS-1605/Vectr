const express = require("express");
const { getSubjects, getLectures } = require("../lecture-pipeline");

function createLectureRoutes() {
  const router = express.Router();

  router.get("/lectures", async (_req, res, next) => {
    try {
      const subjects = await getSubjects();
      const lectures = await getLectures();
      res.json({ subjects, lectures });
    } catch (error) { next(error); }
  });

  router.get("/lectures/subjects", async (_req, res, next) => {
    try { res.json(await getSubjects()); } catch (error) { next(error); }
  });

  router.get("/lectures/:subject", async (req, res, next) => {
    try {
      const slug = req.params.subject.toLowerCase().replace(/\s+/g, "-").replace(/[^a-z0-9-]/g, "").replace(/-+/g, "-").replace(/^-|-$/g, "");
      if (!slug) return res.status(400).json({ error: "Invalid subject." });
      const lectures = await getLectures(slug);
      res.json({ subject: slug, lectures });
    } catch (error) { next(error); }
  });

  return router;
}

module.exports = { createLectureRoutes };
