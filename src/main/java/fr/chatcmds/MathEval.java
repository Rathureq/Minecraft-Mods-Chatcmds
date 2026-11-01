package fr.chatcmds;

/** Petit évaluateur : + - * / % ^ , parenthèses, décimaux, moins unaire. */
public class MathEval {
    private final String s;
    private int pos = -1, ch;

    private MathEval(String s) {
        this.s = s;
    }

    public static String evaluateSafe(String expr) {
        expr = expr.trim();
        if (expr.isEmpty()) return "Usage : !math 2+2";
        if (expr.length() > 100) return "Expression trop longue";
        try {
            double r = new MathEval(expr.replace(',', '.')).parse();
            if (Double.isNaN(r) || Double.isInfinite(r)) return "Résultat invalide";
            return expr + " = " + format(r);
        } catch (ArithmeticException e) {
            return "Erreur : " + e.getMessage();
        } catch (RuntimeException e) {
            return "Expression invalide";
        }
    }

    private static String format(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
        String t = String.format(java.util.Locale.US, "%.8f", v);
        t = t.replaceAll("0+$", "").replaceAll("\\.$", "");
        return t;
    }

    private void next() {
        ch = (++pos < s.length()) ? s.charAt(pos) : -1;
    }

    private boolean eat(int c) {
        while (ch == ' ') next();
        if (ch == c) {
            next();
            return true;
        }
        return false;
    }

    private double parse() {
        next();
        double x = parseExpression();
        if (pos < s.length()) throw new RuntimeException("inattendu : " + (char) ch);
        return x;
    }

    private double parseExpression() {
        double x = parseTerm();
        for (; ; ) {
            if (eat('+')) x += parseTerm();
            else if (eat('-')) x -= parseTerm();
            else return x;
        }
    }

    private double parseTerm() {
        double x = parseFactor();
        for (; ; ) {
            if (eat('*') || eat('x') || eat('×')) x *= parseFactor();
            else if (eat('/') || eat(':') || eat('÷')) {
                double d = parseFactor();
                if (d == 0) throw new ArithmeticException("division par 0");
                x /= d;
            } else if (eat('%')) {
                double d = parseFactor();
                if (d == 0) throw new ArithmeticException("division par 0");
                x %= d;
            } else return x;
        }
    }

    private double parseFactor() {
        if (eat('+')) return parseFactor();
        if (eat('-')) return -parseFactor();

        double x;
        int start = pos;
        if (eat('(')) {
            x = parseExpression();
            if (!eat(')')) throw new RuntimeException("parenthèse manquante");
        } else if ((ch >= '0' && ch <= '9') || ch == '.') {
            while ((ch >= '0' && ch <= '9') || ch == '.') next();
            x = Double.parseDouble(s.substring(start, pos));
        } else {
            throw new RuntimeException("inattendu");
        }

        if (eat('^')) {
            double e = parseFactor();
            if (Math.abs(e) > 1000) throw new ArithmeticException("exposant trop grand");
            x = Math.pow(x, e);
        }
        return x;
    }
}
