package com.koreacb.confluence.aigeneration.ao;

import net.java.ao.Entity;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

@Table("AI_GEN_GLOSSARY")
public interface AoGlossaryTerm extends Entity {

    @Indexed
    String getTerm();
    void setTerm(String term);

    @StringLength(StringLength.UNLIMITED)
    String getDefinition();
    void setDefinition(String definition);

    String getSpaceKey();
    void setSpaceKey(String spaceKey);
}
