const fs = require("fs/promises");
const path = require("path");
const os = require("os");

const lecturesDir = path.join(os.homedir(), "axon", "lectures");

const DAY_NAMES = ["", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"];

function todayYmd() {
  return new Date().toISOString().slice(0, 10);
}

function currentWeekday() {
  const jsDay = new Date().getDay();
  return jsDay === 0 ? 7 : jsDay;
}

function currentHour() {
  return new Date().getHours();
}

function subjectSlug(title) {
  return title.toLowerCase().replace(/\s+/g, "-").replace(/[^a-z0-9-]/g, "").replace(/-+/g, "-").replace(/^-|-$/g, "") || "untitled";
}

async function resolveSubjectFromSchedWall() {
  try {
    const statePath = process.pkg
      ? path.join(os.homedir(), "axon", "schedwall.json")
      : path.join(__dirname, "schedwall.json");
    const raw = await fs.readFile(statePath, "utf8");
    const state = JSON.parse(raw);
    const today = currentWeekday();
    const hour = currentHour();
    const match = (state.template || []).find((t) => t.day === today && hour >= t.start && hour < t.end);
    return match ? { subject: match.title, slug: subjectSlug(match.title), slot: `${match.start}:00-${match.end}:00` } : null;
  } catch { return null; }
}

async function saveLecture(subject, date, transcript, meta = {}) {
  const slug = subjectSlug(subject);
  const subjectDir = path.join(lecturesDir, slug);
  await fs.mkdir(subjectDir, { recursive: true });

  const timeStr = meta.slot || `${String(meta.startHour || 0).padStart(2, "0")}:00-${String(meta.endHour || 0).padStart(2, "0")}:00`;
  const audioFilename = meta.audioFilename || null;
  const title = meta.title || `${subject} — ${date}`;
  const lectureFilename = `lecture-${date}${meta.suffix || ""}.md`;

  const frontmatter = `---\nsubject: ${JSON.stringify(subject)}\ndate: ${JSON.stringify(date)}\ntime: ${JSON.stringify(timeStr)}\ntitle: ${JSON.stringify(title)}${audioFilename ? `\naudio: ${JSON.stringify(audioFilename)}` : ""}\n---\n`;
  const contents = `${frontmatter}\n${transcript}`;
  await fs.writeFile(path.join(subjectDir, lectureFilename), contents, "utf8");

  const readmePath = path.join(subjectDir, "README.md");
  let readme = "";
  try { readme = await fs.readFile(readmePath, "utf8"); } catch {}
  const header = `# ${subject}\n`;
  const entry = `- [${date} (${timeStr}) — ${title}](${lectureFilename})\n`;
  if (!readme.startsWith(header)) {
    readme = `${header}\n${entry}`;
  } else {
    readme = readme.replace(/\n$/, "") + `\n${entry}`;
  }
  await fs.writeFile(readmePath, readme, "utf8");

  return { lectureFilename, subject, slug, date, time: timeStr };
}

async function getSubjects() {
  try {
    const entries = await fs.readdir(lecturesDir, { withFileTypes: true });
    const subjects = [];
    for (const entry of entries) {
      if (!entry.isDirectory()) continue;
      let count = 0;
      try {
        const files = await fs.readdir(path.join(lecturesDir, entry.name));
        count = files.filter((f) => f.startsWith("lecture-") && f.endsWith(".md")).length;
      } catch {}
      subjects.push({ slug: entry.name, count });
    }
    return subjects.sort((a, b) => b.count - a.count);
  } catch { return []; }
}

async function getLectures(subjectSlug) {
  const dir = subjectSlug ? path.join(lecturesDir, subjectSlug) : null;
  if (subjectSlug && dir) {
    try {
      await fs.access(dir);
    } catch { return []; }
  }
  if (dir) {
    const entries = await fs.readdir(dir, { withFileTypes: true });
    const lectures = [];
    for (const entry of entries) {
      if (!entry.isFile() || !entry.name.startsWith("lecture-") || !entry.name.endsWith(".md")) continue;
      const content = await fs.readFile(path.join(dir, entry.name), "utf8");
      const parsed = parseLecture(content);
      if (parsed) lectures.push({ ...parsed, filename: entry.name });
    }
    return lectures.sort((a, b) => b.date.localeCompare(a.date));
  }
  const subjects = await getSubjects();
  const result = {};
  for (const subj of subjects) {
    result[subj.slug] = await getLectures(subj.slug);
  }
  return result;
}

function parseLecture(content) {
  const match = content.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n?([\s\S]*)$/);
  if (!match) return null;
  const frontmatter = match[1];
  const body = match[2].trim();
  function fm(key) {
    const m = frontmatter.match(new RegExp(`^${key}: (.+)$`, "m"));
    if (!m) return "";
    try { return JSON.parse(m[1]); } catch { return m[1]; }
  }
  return {
    subject: fm("subject"),
    date: fm("date"),
    time: fm("time"),
    title: fm("title"),
    audio: fm("audio") || null,
    transcript: body,
    preview: body.slice(0, 150),
  };
}

module.exports = { saveLecture, getSubjects, getLectures, resolveSubjectFromSchedWall, subjectSlug, lecturesDir };
