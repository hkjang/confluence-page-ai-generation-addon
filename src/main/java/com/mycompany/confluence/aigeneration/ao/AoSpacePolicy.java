package com.mycompany.confluence.aigeneration.ao;

import net.java.ao.Entity;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;
import net.java.ao.schema.Unique;

@Table("AI_GEN_POLICY")
public interface AoSpacePolicy extends Entity {

    @Unique
    @Indexed
    String getSpaceKey();
    void setSpaceKey(String spaceKey);

    int getMaxRequestsPerDay();
    void setMaxRequestsPerDay(int maxRequestsPerDay);

    int getMaxTokensPerRequest();
    void setMaxTokensPerRequest(int maxTokensPerRequest);

    @StringLength(StringLength.UNLIMITED)
    String getAllowedGroups();
    void setAllowedGroups(String allowedGroups);

    @StringLength(StringLength.UNLIMITED)
    String getAllowedTemplates();
    void setAllowedTemplates(String allowedTemplates);

    boolean isEnabled();
    void setEnabled(boolean enabled);
}
