package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;

/**
 * @author Maxim.Medvedev
 */
public class GrStringUtil {
  private static final Logger LOG = Logger.getInstance(GrStringUtil.class);

  private static final String TRIPLE_QUOTES = "'''";
  private static final String QUOTE = "'";
  private static final String DOUBLE_QUOTES = "\"";
  private static final String TRIPLE_DOUBLE_QUOTES = "\"\"\"";
  private static final String SLASH = "/";

  private GrStringUtil() {
  }

  public static String escapeSymbolsForGString(String s, boolean escapeDoubleQuotes) {
    return escapeSymbolsForGString(s, escapeDoubleQuotes, false);
  }

  public static String escapeSymbolsForGString(String s, boolean escapeDoubleQuotes, boolean forInjection) {
    StringBuilder b = new StringBuilder();
    escapeStringCharacters(s.length(), s, escapeDoubleQuotes ? "$\"" : "$", false, forInjection, b);
    if (!forInjection) {
      unescapeCharacters(b, escapeDoubleQuotes ? "'" : "'\"", true);
    }
    return b.toString();
  }

  public static String escapeSymbolsForString(String s, boolean escapeQuotes) {
    return escapeSymbolsForString(s, escapeQuotes, false);
  }

  public static String escapeSymbolsForString(String s, boolean escapeQuotes, boolean forInjection) {
    final StringBuilder builder = new StringBuilder();
    escapeStringCharacters(s.length(), s, escapeQuotes ? "'" : "", false, forInjection, builder);
    if (!forInjection) {
      unescapeCharacters(builder, escapeQuotes ? "$\"" : "$'\"", true);
    }
    return builder.toString();
  }

  @NotNull
  public static StringBuilder escapeStringCharacters(int length,
                                                     @NotNull String str,
                                                     @Nullable String additionalChars,
                                                     boolean escapeNR,
                                                     boolean escapeSlash,
                                                     @NotNull @NonNls StringBuilder buffer) {
    for (int idx = 0; idx < length; idx++) {
      char ch = str.charAt(idx);
      switch (ch) {
        case '\b':
          buffer.append("\\b");
          break;

        case '\t':
          buffer.append("\\t");
          break;

        case '\f':
          buffer.append("\\f");
          break;

        case '\\':
          if (escapeSlash) {
            buffer.append("\\\\");
          }
          else {
            buffer.append("\\");
          }
          break;

        case '\n':
          if (escapeNR) {
            buffer.append("\\n");
          }
          else {
            buffer.append('\n');
          }
          break;

        case '\r':
          if (escapeNR) {
            buffer.append("\\r");
          }
          else {
            buffer.append('\r');
          }
          break;

        default:
          if (additionalChars != null && additionalChars.indexOf(ch) > -1) {
            buffer.append("\\").append(ch);
          }
          else if (Character.isISOControl(ch)) {
            String hexCode = Integer.toHexString(ch).toUpperCase();
            buffer.append("\\u");
            int paddingCount = 4 - hexCode.length();
            while (paddingCount-- > 0) {
              buffer.append(0);
            }
            buffer.append(hexCode);
          }
          else {
            buffer.append(ch);
          }
      }
    }
    return buffer;
  }

  private static void unescapeCharacters(StringBuilder builder, String toUnescape, boolean isMultiLine) {
    for (int i = 0; i < builder.length(); i++) {
      if (builder.charAt(i) != '\\') continue;
      if (i + 1 == builder.length()) break;
      char next = builder.charAt(i + 1);
      if (next == 'n') {
        if (isMultiLine) {
          builder.replace(i, i + 2, "\n");
        }
      }
      else if (next == 'r') {
        if (isMultiLine) {
          builder.replace(i, i + 2, "\r");
        }
      }
      else if (toUnescape.indexOf(next) != -1) {
        builder.delete(i, i + 1);
      }
      else {
        i++;
      }
    }
  }

  public static String escapeSymbols(String s, String toEscape) {
    StringBuilder builder = new StringBuilder();
    boolean escaped = false;
    for (int i = 0; i < s.length(); i++) {
      final char ch = s.charAt(i);
      if (escaped) {
        builder.append(ch);
        escaped = false;
        continue;
      }
      if (ch == '\\') {
        escaped = true;
      }
      if (toEscape.indexOf(ch) >= 0) {
        builder.append('\\');
      }

      builder.append(ch);
    }
    return builder.toString();
  }

  public static String removeQuotes(@NotNull String s) {
    String quote = getStartQuote(s);
    int sL = s.length();
    int qL = quote.length();
    if (sL >= qL * 2 && s.endsWith(quote)) {
      return s.substring(qL, sL - qL);
    }
    return s;
  }

  public static String addQuotes(String s, boolean forGString) {
    if (forGString) {
      if (s.contains("\n") || s.contains("\r")) {
        return TRIPLE_DOUBLE_QUOTES + s + TRIPLE_DOUBLE_QUOTES;
      }
      else {
        return DOUBLE_QUOTES + s + DOUBLE_QUOTES;
      }
    }
    else {
      if (s.contains("\n") || s.contains("\r")) {
        return TRIPLE_QUOTES + s + TRIPLE_QUOTES;
      }
      else {
        return QUOTE + s + QUOTE;
      }
    }
  }

  public static GrString replaceStringInjectionByLiteral(GrStringInjection injection, GrLiteral literal) {
    GrString grString = (GrString)injection.getParent();

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(grString.getProject());

    String literalText;

    //wrap last injection in inserted literal if it needed
    // e.g.: "bla bla ${foo}bla bla" and {foo} is replaced
    if (literal instanceof GrString) {
      final GrStringInjection[] injections = ((GrString)literal).getInjections();
      if (injections.length > 0) {
        if (injections[injections.length - 1].getExpression() != null) {
          if (!checkBraceIsUnnecessary(injections[injections.length - 1].getExpression(), injection.getNextSibling())) {
            wrapInjection(injections[injections.length - 1]);
          }
        }
      }
      literalText = removeQuotes(literal.getText());
    }
    else {
      final String text = removeQuotes(literal.getText());
      literalText = escapeSymbolsForGString(text, !text.contains("\n") && grString.isPlainString());
    }

    if (literalText.contains("\n")) {
      wrapGStringInto(grString, TRIPLE_DOUBLE_QUOTES);
    }
    
    final GrExpression expression = factory.createExpressionFromText("\"\"\"${}" + literalText + "\"\"\"");

    expression.getFirstChild().delete();
    expression.getFirstChild().delete();

    final ASTNode node = grString.getNode();
    if (expression.getFirstChild() != null) {
      if (expression.getFirstChild() == expression.getLastChild()) {
        node.replaceChild(injection.getNode(), expression.getFirstChild().getNode());
      }
      else {
        node.addChildren(expression.getFirstChild().getNode(), expression.getLastChild().getNode(), injection.getNode());
        node.removeChild(injection.getNode());
      }
    }
    return grString;
  }

  private static void wrapGStringInto(GrString grString, String quotes) {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(grString.getProject());
    final PsiElement firstChild = grString.getFirstChild();
    final PsiElement lastChild = grString.getLastChild();

    final GrExpression template = factory.createExpressionFromText(quotes + "$x" + quotes);
    if (firstChild != null &&
        firstChild.getNode().getElementType() == GroovyTokenTypes.mGSTRING_BEGIN &&
        !quotes.equals(firstChild.getText())) {
      grString.getNode().replaceChild(firstChild.getNode(), template.getFirstChild().getNode());
    }
    if (lastChild != null &&
        lastChild.getNode().getElementType() == GroovyTokenTypes.mGSTRING_END &&
        !quotes.equals(lastChild.getText())) {
      grString.getNode().replaceChild(lastChild.getNode(), template.getLastChild().getNode());
    }
  }

  public static void wrapInjection(GrStringInjection injection) {
    final GrExpression expression = injection.getExpression();
    LOG.assertTrue(expression != null);
    final GroovyPsiElementFactory instance = GroovyPsiElementFactory.getInstance(injection.getProject());
    final GrClosableBlock closure = instance.createClosureFromText("{" + expression.getText() + "}");
    injection.getNode().replaceChild(expression.getNode(), closure.getNode());
  }

  public static boolean checkGStringInjectionForUnnecessaryBraces(GrStringInjection injection) {
    final GrClosableBlock block = injection.getClosableBlock();
    if (block == null) return false;

    final GrStatement[] statements = block.getStatements();
    if (statements.length != 1) return false;

    if (!(statements[0] instanceof GrReferenceExpression)) return false;

    final PsiElement next = injection.getNextSibling();
    if (!(next instanceof LeafPsiElement)) return false;

    return checkBraceIsUnnecessary(statements[0], next);
  }

  private static boolean checkBraceIsUnnecessary(GrStatement injected, PsiElement next) {
    char nextChar = next.getText().charAt(0);
    if (nextChar == '"' || nextChar == '$') {
      return true;
    }
    final GroovyPsiElementFactory elementFactory = GroovyPsiElementFactory.getInstance(injected.getProject());
    final GrExpression gString;
    try {
      gString = elementFactory.createExpressionFromText("\"$" + injected.getText() + nextChar + '"');
    }
    catch (Exception e) {
      return false;
    }
    if (!(gString instanceof GrString)) return false;

    final PsiElement child = gString.getChildren()[0];
    if (!(child instanceof GrStringInjection)) return false;

    final PsiElement refExprCopy = ((GrStringInjection)child).getExpression();
    if (!(refExprCopy instanceof GrReferenceExpression)) return false;

    final GrReferenceExpression refExpr = (GrReferenceExpression)injected;
    return Comparing.equal(refExpr.getName(), ((GrReferenceExpression)refExprCopy).getName());
  }

  public static void removeUnnecessaryBracesInGString(GrString grString) {
    for (GrStringInjection child : grString.getInjections()) {
      if (checkGStringInjectionForUnnecessaryBraces(child)) {
        final GrClosableBlock closableBlock = child.getClosableBlock();
        final GrReferenceExpression refExpr = (GrReferenceExpression)closableBlock.getStatements()[0];
        final GrReferenceExpression copy = (GrReferenceExpression)refExpr.copy();
        final ASTNode oldNode = closableBlock.getNode();
        oldNode.getTreeParent().replaceChild(oldNode, copy.getNode());
      }
    }
  }

  public static String getStartQuote(String text) {
    if (text.startsWith(TRIPLE_QUOTES)) return TRIPLE_QUOTES;
    if (text.startsWith(QUOTE)) return QUOTE;
    if (text.startsWith(TRIPLE_DOUBLE_QUOTES)) return TRIPLE_DOUBLE_QUOTES;
    if (text.startsWith(DOUBLE_QUOTES)) return DOUBLE_QUOTES;
    if (text.startsWith(SLASH)) return SLASH;
    return "";
  }

  /**
   * @see com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl#parseStringCharacters(String, StringBuilder, int[])
   */
  public static boolean parseStringCharacters(@NotNull String chars,
                                              @NotNull StringBuilder outChars,
                                              @Nullable int[] sourceOffsets,
                                              final boolean strictBackSlash) {
    assert sourceOffsets == null || sourceOffsets.length == chars.length()+1;
    if (chars.indexOf('\\') < 0) {
      outChars.append(chars);
      if (sourceOffsets != null) {
        for (int i = 0; i < sourceOffsets.length; i++) {
          sourceOffsets[i] = i;
        }
      }
      return true;
    }
    int index = 0;
    final int outOffset = outChars.length();
    while (index < chars.length()) {
      char c = chars.charAt(index++);
      if (sourceOffsets != null) {
        sourceOffsets[outChars.length()-outOffset] = index - 1;
        sourceOffsets[outChars.length() + 1 -outOffset] = index;
      }
      if (c != '\\') {
        outChars.append(c);
        continue;
      }
      if (index == chars.length()) return false;
      c = chars.charAt(index++);
      switch (c) {
        case'b':
        case't':
        case'n':
        case'f':
        case'r':
        case'"':
        case'\'':
        case'$':
        case'\\':
          outChars.append(c);
          break;
        case '\n':
          //do nothing
          break;

        case'0':
        case'1':
        case'2':
        case'3':
        case'4':
        case'5':
        case'6':
        case'7':
          char startC = c;
          int v = (int)c - '0';
          if (index < chars.length()) {
            c = chars.charAt(index++);
            if ('0' <= c && c <= '7') {
              v <<= 3;
              v += c - '0';
              if (startC <= '3' && index < chars.length()) {
                c = chars.charAt(index++);
                if ('0' <= c && c <= '7') {
                  v <<= 3;
                  v += c - '0';
                }
                else {
                  index--;
                }
              }
            }
            else {
              index--;
            }
          }
          outChars.append((char)v);
          break;

        case'u':
          // uuuuu1234 is valid too
          while (index != chars.length() && chars.charAt(index) == 'u') {
            index++;
          }
          if (index + 4 <= chars.length()) {
            try {
              int code = Integer.parseInt(chars.substring(index, index + 4), 16);
              //line separators are invalid here
              if (code == 0x000a || code == 0x000d) return false;
              c = chars.charAt(index);
              if (c == '+' || c == '-') return false;
              outChars.append((char)code);
              index += 4;
            }
            catch (Exception e) {
              return false;
            }
          }
          else {
            return false;
          }
          break;
        default:
          if (!strictBackSlash) {
            break;
          }
          return false;
      }
      if (sourceOffsets != null) {
        sourceOffsets[outChars.length()-outOffset] = index;
      }
    }
    return true;
  }
}
