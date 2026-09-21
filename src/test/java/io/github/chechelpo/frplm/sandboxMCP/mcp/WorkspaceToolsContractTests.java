package io.github.chechelpo.frplm.sandboxMCP.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpToolParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WorkspaceToolsContractTests {

    @Test
    void workspaceToolsExposeAnOptionalWorkspaceNameParameter() {
        List<String> workspaceToolNames = List.of(
                "execute",
                "readFile",
                "writeFile",
                "editFile"
        );

        for (String methodName : workspaceToolNames) {
            Method method = Arrays.stream(WorkspaceTools.class.getMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            Parameter workspaceName = method.getParameters()[
                    method.getParameterCount() - 1
            ];

            assertEquals("workspaceName", workspaceName.getName());
            assertFalse(workspaceName.getAnnotation(McpToolParam.class).required());
        }
    }

    @Test
    void stableWorkspaceCreationUsesANameAndDescription() throws Exception {
        Method create = WorkspaceTools.class.getMethod(
                "createStableWorkspace",
                String.class,
                String.class
        );

        assertEquals("name", create.getParameters()[0].getName());
        assertEquals("description", create.getParameters()[1].getName());
    }
}
