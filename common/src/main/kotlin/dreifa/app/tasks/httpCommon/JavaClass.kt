package dreifa.app.tasks.httpCommon

class JavaClass(fullName: String) {
    private val parts = fullName.split(".")
    fun shortName(): String = parts[parts.size - 1]

}