package com.mycompany.confluence.aigeneration.ao;

import net.java.ao.Entity;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;
import java.util.Date;

@Table("AI_GEN_PROMPT")
public interface AoPromptTemplate extends Entity {

    @Indexed
    String getTemplateKey();
    void setTemplateKey(String templateKey);

    String getDisplayName();
    void setDisplayName(String displayName);

    @StringLength(StringLength.UNLIMITED)
    String getSystemPrompt();
    void setSystemPrompt(String systemPrompt);

    @StringLength(StringLength.UNLIMITED)
    String getSectionPrompts();
    void setSectionPrompts(String sectionPrompts);

    boolean isActive();
    void setActive(boolean active);

    Date getLastModified();
    void setLastModified(Date lastModified);

    String getModifiedByUserKey();
    void setModifiedByUserKey(String modifiedByUserKey);
}
