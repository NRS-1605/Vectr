package com.vectr

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Course(
    val id: String,
    val courseId: String,
    val courseName: String,
    val credits: Int,
    val grade: String,
)

data class Semester(
    val name: String,
    val courses: MutableList<Course> = mutableListOf(),
)

object CgpaRepository {
    private const val PREFS_NAME = "vectr_cgpa"
    private const val KEY_DATA = "semesters"

    val SEMESTER_NAMES = listOf("1-1", "1-2", "2-1", "2-2", "3-1", "3-2", "4-1", "4-2", "PS1", "PS2")

    val GRADE_POINTS = mapOf(
        "A" to 10, "A-" to 9,
        "B" to 8, "B-" to 7,
        "C" to 6, "C-" to 5,
        "D" to 4, "F" to 0,
    )

    val GRADE_ORDER = listOf("A", "A-", "B", "B-", "C", "C-", "D", "F")

    fun load(context: Context): List<Semester> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_DATA, null)
        if (json == null) {
            return SEMESTER_NAMES.map { Semester(it) }
        }
        val arr = JSONArray(json)
        val semesters = mutableListOf<Semester>()
        for (i in 0 until arr.length()) {
            val semester = parseSemester(arr.getJSONObject(i))
            semesters.add(semester)
        }
        // Ensure all semesters exist, preserving existing data
        return SEMESTER_NAMES.map { name ->
            semesters.find { it.name == name } ?: Semester(name)
        }
    }

    fun save(context: Context, semesters: List<Semester>) {
        val arr = JSONArray()
        semesters.forEach { s -> arr.put(serializeSemester(s)) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_DATA, arr.toString()).apply()
    }

    fun addCourse(context: Context, semesters: List<Semester>, semesterName: String, course: Course) {
        val semester = semesters.find { it.name == semesterName } ?: return
        semester.courses.add(course)
        save(context, semesters)
    }

    fun deleteCourse(context: Context, semesters: List<Semester>, semesterName: String, courseId: String) {
        val semester = semesters.find { it.name == semesterName } ?: return
        semester.courses.removeAll { it.id == courseId }
        save(context, semesters)
    }

    fun calculateCgpa(semesters: List<Semester>): Pair<Double, Int> {
        var totalPoints = 0.0
        var totalCredits = 0
        semesters.forEach { sem ->
            sem.courses.forEach { course ->
                val gradePoint = GRADE_POINTS[course.grade] ?: 0
                totalPoints += gradePoint * course.credits
                totalCredits += course.credits
            }
        }
        val cgpa = if (totalCredits > 0) totalPoints / totalCredits else 0.0
        return cgpa to totalCredits
    }

    fun totalCourses(semesters: List<Semester>): Int {
        return semesters.sumOf { it.courses.size }
    }

    private fun parseSemester(json: JSONObject): Semester {
        val name = json.optString("name", "")
        val courses = json.optJSONArray("courses")?.let { arr ->
            (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                Course(
                    id = c.optString("id", ""),
                    courseId = c.optString("courseId", ""),
                    courseName = c.optString("courseName", ""),
                    credits = c.optInt("credits", 0),
                    grade = c.optString("grade", ""),
                )
            }.toMutableList()
        } ?: mutableListOf()
        return Semester(name, courses)
    }

    private fun serializeSemester(semester: Semester): JSONObject {
        val arr = JSONArray()
        semester.courses.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("courseId", c.courseId)
                put("courseName", c.courseName)
                put("credits", c.credits)
                put("grade", c.grade)
            })
        }
        return JSONObject().apply {
            put("name", semester.name)
            put("courses", arr)
        }
    }
}
