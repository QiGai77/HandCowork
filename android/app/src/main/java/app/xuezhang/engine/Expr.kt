package app.xuezhang.engine

import kotlin.math.*

/**
 * 极小的安全表达式解析器：只支持数字、变量、+ - * / ^、括号和白名单函数。
 * 不使用脚本引擎，不支持赋值/循环/字符串，长度与嵌套深度都有上限。
 */
class ExprException(msg: String) : Exception(msg)

sealed class Expr {
    abstract fun eval(v: Map<String, Double>): Double

    data class Num(val x: Double) : Expr() { override fun eval(v: Map<String, Double>) = x }
    data class Var(val n: String) : Expr() {
        override fun eval(v: Map<String, Double>) = v[n] ?: when (n) { "pi", "π" -> PI; "e" -> E; else -> throw ExprException("未知变量 $n") }
    }
    data class Neg(val a: Expr) : Expr() { override fun eval(v: Map<String, Double>) = -a.eval(v) }
    data class Bin(val op: Char, val a: Expr, val b: Expr) : Expr() {
        override fun eval(v: Map<String, Double>): Double {
            val x = a.eval(v); val y = b.eval(v)
            return when (op) { '+' -> x + y; '-' -> x - y; '*' -> x * y; '/' -> x / y; '^' -> x.pow(y); else -> Double.NaN }
        }
    }
    data class Fn(val name: String, val args: List<Expr>) : Expr() {
        override fun eval(v: Map<String, Double>): Double {
            val a = args.map { it.eval(v) }
            fun one() = a[0]
            return when (name) {
                "sin" -> sin(one()); "cos" -> cos(one()); "tan" -> tan(one())
                "asin" -> asin(one()); "acos" -> acos(one()); "atan" -> atan(one())
                "sinh" -> sinh(one()); "cosh" -> cosh(one()); "tanh" -> tanh(one())
                "exp" -> exp(one()); "ln" -> ln(one()); "log" -> log10(one()); "log2" -> log2(one())
                "sqrt" -> sqrt(one()); "abs" -> abs(one()); "floor" -> floor(one()); "ceil" -> ceil(one())
                "sign" -> sign(one()); "deg" -> Math.toDegrees(one()); "rad" -> Math.toRadians(one())
                "min" -> a.minOrNull() ?: Double.NaN; "max" -> a.maxOrNull() ?: Double.NaN
                "pow" -> a[0].pow(a[1]); "atan2" -> atan2(a[0], a[1])
                else -> throw ExprException("不支持的函数 $name")
            }
        }
    }

    companion object {
        private val FN_ARITY = mapOf(
            "sin" to 1, "cos" to 1, "tan" to 1, "asin" to 1, "acos" to 1, "atan" to 1, "sinh" to 1, "cosh" to 1, "tanh" to 1,
            "exp" to 1, "ln" to 1, "log" to 1, "log2" to 1, "sqrt" to 1, "abs" to 1, "floor" to 1, "ceil" to 1, "sign" to 1,
            "deg" to 1, "rad" to 1, "min" to -1, "max" to -1, "pow" to 2, "atan2" to 2)
        const val MAX_LEN = 400
        const val MAX_DEPTH = 40

        fun parse(src: String): Expr {
            if (src.length > MAX_LEN) throw ExprException("表达式太长")
            val p = P(src.replace('×', '*').replace('·', '*').replace('−', '-').replace("**", "^"))
            val e = p.expr(0)
            p.ws(); if (p.i < p.s.length) throw ExprException("多余的字符 '${p.s[p.i]}'")
            return e
        }

        /** 解析失败返回 null，不抛异常。 */
        fun tryParse(src: String?): Expr? = if (src.isNullOrBlank()) null else runCatching { parse(src) }.getOrNull()

        /** 求值；任何异常/非有限值返回 NaN。 */
        fun safeEval(e: Expr?, v: Map<String, Double>): Double =
            if (e == null) Double.NaN else runCatching { e.eval(v) }.getOrDefault(Double.NaN).let { if (it.isFinite()) it else Double.NaN }

        fun vars(e: Expr): Set<String> = when (e) {
            is Num -> emptySet(); is Var -> setOf(e.n); is Neg -> vars(e.a)
            is Bin -> vars(e.a) + vars(e.b); is Fn -> e.args.flatMap { vars(it) }.toSet()
        }

        private class P(val s: String) {
            var i = 0
            fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
            fun peek(): Char? { ws(); return if (i < s.length) s[i] else null }
            fun depth(d: Int) { if (d > MAX_DEPTH) throw ExprException("嵌套太深") }

            fun expr(d: Int): Expr {
                depth(d)
                var l = term(d + 1)
                while (true) {
                    val c = peek()
                    if (c == '+' || c == '-') { i++; l = Bin(c, l, term(d + 1)) } else return l
                }
            }
            fun term(d: Int): Expr {
                depth(d)
                var l = unary(d + 1)
                while (true) {
                    val c = peek()
                    when {
                        c == '*' || c == '/' -> { i++; l = Bin(c, l, unary(d + 1)) }
                        // 隐式乘法：2x、2(x+1)、(a)(b)
                        c != null && (c.isLetter() || c == '(' || c == 'π') -> l = Bin('*', l, unary(d + 1))
                        else -> return l
                    }
                }
            }
            fun unary(d: Int): Expr {
                depth(d)
                val c = peek()
                if (c == '-') { i++; return Neg(unary(d + 1)) }
                if (c == '+') { i++; return unary(d + 1) }
                return power(d + 1)
            }
            fun power(d: Int): Expr {
                depth(d)
                val b = atom(d + 1)
                if (peek() == '^') { i++; return Bin('^', b, unary(d + 1)) } // 右结合
                return b
            }
            fun atom(d: Int): Expr {
                depth(d)
                val c = peek() ?: throw ExprException("表达式不完整")
                if (c == '(') {
                    i++; val e = expr(d + 1)
                    if (peek() != ')') throw ExprException("缺少右括号"); i++; return e
                }
                if (c.isDigit() || c == '.') {
                    val st = i
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                    if (i < s.length && (s[i] == 'e' || s[i] == 'E') && i + 1 < s.length && (s[i + 1].isDigit() || ((s[i + 1] == '-' || s[i + 1] == '+') && i + 2 < s.length && s[i + 2].isDigit()))) {
                        i += 2; while (i < s.length && s[i].isDigit()) i++
                    }
                    return Num(s.substring(st, i).toDoubleOrNull() ?: throw ExprException("数字格式错误"))
                }
                if (c.isLetter() || c == '_' || c == 'π') {
                    val st = i
                    while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_' || s[i] == 'π')) i++
                    val name = s.substring(st, i)
                    if (peek() == '(' && name in FN_ARITY) {
                        i++
                        val args = mutableListOf<Expr>()
                        if (peek() != ')') {
                            args.add(expr(d + 1))
                            while (peek() == ',') { i++; args.add(expr(d + 1)) }
                        }
                        if (peek() != ')') throw ExprException("函数缺少右括号"); i++
                        val ar = FN_ARITY.getValue(name)
                        if ((ar > 0 && args.size != ar) || (ar < 0 && args.isEmpty())) throw ExprException("$name 参数个数不对")
                        return Fn(name, args)
                    }
                    if (name in FN_ARITY) throw ExprException("$name 后面要有括号")
                    return Var(name)
                }
                throw ExprException("无法识别的字符 '$c'")
            }
        }
    }
}
