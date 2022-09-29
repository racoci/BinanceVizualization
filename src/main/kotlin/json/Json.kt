package json

import arrow.typeclasses.Semiring
import kotlinx.serialization.json.JsonElement


sealed interface Json {
    object JSON : Json
    class JsonList(vararg val children: Json) : Json
    class JsonObject(vararg val children: JsonPair<out Json>) : Json
    sealed interface JsonSimple<SimpleValue> : Json {
        val value: SimpleValue
        data class JsonString(override val value: String) : JsonSimple<String>
        data class JsonBoolean(override val value: Boolean) : JsonSimple<Boolean>
        data class JsonNumber(override val value: Number) : JsonSimple<Number>
    }
}


fun json(vararg children: Json): Json = Json.JsonList(*children)
fun json(vararg children: JsonPair<out Json>): Json = Json.JsonObject(*children)
fun json(value: Boolean): Json = Json.JsonSimple.JsonBoolean(value)
fun json(value: String): Json = Json.JsonSimple.JsonString(value)
fun json(value: Number): Json = Json.JsonSimple.JsonNumber(value)
fun json(): Json = Json.JSON

data class JsonPair<ValueType : Json>(val key: String, val value: ValueType)

infix fun String.to(value: Number) = JsonPair(this, value.json)
infix fun String.to(value: Boolean) = JsonPair(this, value.json)
infix fun String.to(value: String) = JsonPair(this, value.json)
infix fun String.to(value: Json) = JsonPair(this, value)

interface JsonConvertable{
    val json: Json
}

val String.json get() = json(this)
val Boolean.json get() = json(this)
val Number.json get() = json(this)

fun teste() {
    json(
        json(
            json(
                "" to json(
                    ""
                ),
                "" to 0,
                "" to true,
            ), json(
                0.json,
                true.json,
                "".json,
                json(),
            )
        )
    )
}


