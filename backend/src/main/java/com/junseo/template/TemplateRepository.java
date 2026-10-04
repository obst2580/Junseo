package com.junseo.template;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateRepository extends JpaRepository<Template, String> {

    /** Shown order: higher sort order first, then the newest. */
    List<Template> findByActiveTrueOrderBySortOrderDescCreatedAtDesc();
}
