package ua.ruhaysia.app

/** Exercises that can close a reminder. */
enum class Ex(
    val key: String,
    val title: String,
    val defaultGoal: Int,
    val goals: List<Int>,
    val hint: String
) {
    STEPS(
        "steps", "Кроки", 100, listOf(50, 100, 150, 200, 300, 500),
        "Просто пройдись із телефоном у руці або в кишені."
    ),
    SQUATS(
        "squats", "Присідання", 15, listOf(10, 15, 20, 25, 30),
        "Тримай телефон двома руками біля грудей і присідай."
    ),
    JUMPS(
        "jumps", "Стрибки", 20, listOf(10, 15, 20, 30, 40, 50),
        "Міцно притисни телефон до грудей або поклади в кишеню і стрибай на місці."
    ),
    ARMS(
        "arms", "Махи руками", 30, listOf(20, 30, 40, 50, 60),
        "Візьми телефон у руку і роби махи або кола прямою рукою. Тримай міцно!"
    ),
    LEGS(
        "legs", "Махи ногами", 20, listOf(10, 20, 30, 40),
        "Поклади телефон у передню кишеню штанів і роби махи цією ногою."
    )
}
