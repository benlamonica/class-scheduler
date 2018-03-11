package us.pojo.scheduling;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;


public class CSVParser {
    private static final Pattern NEXT_CELL = Pattern.compile("(\"([^\"]*)\"|([^\",])*),");
    public static List<String> parseLine(String line) {
        List<String> values = new ArrayList<>();
        Matcher m = NEXT_CELL.matcher(line);
        String end = null;
        while(m.find()) {
            if (m.group(2) != null) {
                values.add(m.group(2));
            } else {
                values.add(m.group(1));
            }
            end = line.substring(m.end());
        }

        if (StringUtils.isNotBlank(end)) {
            values.add(end);
        }

        return values;
    }
}
