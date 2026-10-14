package org.example.utils;

import io.cucumber.spring.ScenarioScope;
import org.springframework.stereotype.Component;

/** Nur für {@code CucumberGlueConfigTest}: belegt, dass Beans aus {@code org.example.utils} gefunden werden. */
@Component
@ScenarioScope
public class GlueScanProbe {
}
