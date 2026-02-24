package com.mycompany.confluence.aigeneration.ao;

import net.java.ao.Entity;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.Table;

@Table("AI_GEN_FORBIDDEN")
public interface AoForbiddenWord extends Entity {

    @Indexed
    String getPattern();
    void setPattern(String pattern);

    String getReplacement();
    void setReplacement(String replacement);

    boolean isRegex();
    void setRegex(boolean regex);

    String getSpaceKey();
    void setSpaceKey(String spaceKey);
}
