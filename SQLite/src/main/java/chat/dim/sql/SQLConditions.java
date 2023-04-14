/* license: https://mit-license.org
 *
 *  DIMP : Decentralized Instant Messaging Protocol
 *
 *                                Written in 2022 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Albert Moky
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * ==============================================================================
 */
package chat.dim.sql;

public final class SQLConditions {

    private Condition condition = null;

    public void appendEscapeValue(StringBuilder sb) {
        if (condition != null) {
            condition.appendEscapeValue(sb);
        }
    }

    public void addCondition(Relation relation, String name, String operator, Object value) {
        Condition newCondition = createCondition(name, operator, value);
        addCondition(relation, newCondition);
    }

    private void addCondition(Relation relation, Condition newCondition) {
        if (condition == null) {
            condition = newCondition;
        } else {
            condition = createCondition(condition, relation, newCondition);
        }
    }

    private static Condition createCondition(String name, String operator, Object value) {
        return new CompareCondition(name, operator, value);
    }
    private static Condition createCondition(Condition left, Relation relation, Condition right) {
        return new RelatedCondition(left, relation, right);
    }

    //
    //  Conditions
    //

    public interface Condition {

        void appendEscapeValue(StringBuilder sb);
    }

    static final class CompareCondition implements Condition {
        private final String name;
        private final String operator;
        private final Object value;

        CompareCondition(String left, String op, Object right) {
            name = left;
            operator = op;
            value = right;
        }

        @Override
        public void appendEscapeValue(StringBuilder sb) {
            sb.append(name);
            sb.append(operator);
            SQLValues.appendEscapeValue(sb, value);
        }
    }

    static final class RelatedCondition implements Condition {
        private final Condition condition1;
        private final Relation relation;
        private final Condition condition2;

        RelatedCondition(Condition left, Relation op, Condition right) {
            condition1 = left;
            relation = op;
            condition2 = right;
        }

        private static void appendEscapeValue(StringBuilder sb, Condition condition) {
            if (condition instanceof RelatedCondition) {
                sb.append("(");
                condition.appendEscapeValue(sb);
                sb.append(")");
            } else {
                condition.appendEscapeValue(sb);
            }
        }

        @Override
        public void appendEscapeValue(StringBuilder sb) {
            appendEscapeValue(sb, condition1);
            switch (relation) {
                case AND:
                    sb.append(" AND ");
                    break;
                case OR:
                    sb.append(" OR ");
                    break;
                default:
                    throw new AssertionError("relation operator error: " + relation);
            }
            appendEscapeValue(sb, condition2);
        }
    }

    public enum Relation {
        AND,
        OR
    }
}
