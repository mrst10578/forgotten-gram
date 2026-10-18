package ir.forgottengram.archive;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.assertEquals;

public class TemplateEngineTest {
    @Test public void replacesSimpleFields() {
        assertEquals("جلسه ۱۲ - زیست", TemplateEngine.render("جلسه {session} - {subject}",
                Map.of("session", "۱۲", "subject", "زیست")));
    }
    @Test public void preservesUnknownFields() {
        assertEquals("{unknown}", TemplateEngine.render("{unknown}", Map.of()));
    }
    @Test public void doesNotExpandFieldsInsideSuppliedCaption() {
        assertEquals("{session}", TemplateEngine.render("{caption}", Map.of("caption", "{session}", "session", "3")));
    }
}
