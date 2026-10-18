package ir.forgottengram.archive;

import java.util.Map;

public final class TemplateEngine {
    private TemplateEngine() {}
    public static String render(String pattern, Map<String, String> values) {
        if (pattern == null) return "";
        StringBuilder output = new StringBuilder();
        // Resolve placeholders in one pass so source captions never become template instructions.
        java.util.regex.Matcher matcher =
            java.util.regex.Pattern.compile("\\{([a-z_]+)\\}").matcher(pattern);
        int position = 0;
        while (matcher.find()) {
            output.append(pattern, position, matcher.start());
            String key = matcher.group(1);
            output.append(values.containsKey(key) ? String.valueOf(values.get(key)) : matcher.group());
            position = matcher.end();
        }
        return output.append(pattern.substring(position)).toString();
    }
}
