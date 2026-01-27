package org.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CucumberController {

    private final CucumberRunnerService runnerService;

    public CucumberController(CucumberRunnerService runnerService) {
        this.runnerService = runnerService;
    }

    @RequestMapping(value = "/run", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<CucumberRunnerService.RunResult> run(@RequestParam String label) throws Exception {
        return ResponseEntity.ok(runnerService.runByLabel(label));
    }
}
