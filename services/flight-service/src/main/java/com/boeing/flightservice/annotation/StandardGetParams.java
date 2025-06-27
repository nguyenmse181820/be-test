package com.boeing.flightservice.annotation;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Parameters({
        @Parameter(
                name = "pageNo",
                description = "Page number for pagination. Disable paging if pageNo or pageSize aren't provided."
        ),
        @Parameter(
                name = "pageSize",
                description = "Number of items per page. Disable paging if pageNo or pageSize aren't provided."
        ),
        @Parameter(
                name = "sortBy",
                description = "Sorting by fields. Example: field1:desc, field2:asc (default ascending if not provided direction)."
        ),
        @Parameter(
                name = "params",
                description = "Specify which fields to be returned (separated by comma)."
        ),
        @Parameter(
                name = "id",
                description = "Find by ID (UUID)"
        )
})
public @interface StandardGetParams {
}
