package io.github.kurok1.jsonschema.examples;

import io.github.kurok1.jsonschema.annotations.InheritanceStrategy;
import io.github.kurok1.jsonschema.annotations.JsonSchema;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@JsonSchema(
        title = "Employee",
        description = "Demonstrates FLATTEN inheritance: parent fields are inlined directly into this schema's properties instead of going through allOf.",
        inheritance = InheritanceStrategy.FLATTEN
)
public class Employee extends Person {

    @NotNull
    private String employeeId;

    private String department;

    private BigDecimal annualSalary;
}
