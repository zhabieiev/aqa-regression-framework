package com.aqa.mcp.validation;

import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.ast.PackageDeclaration;

/** Derives a module's base Java package from its own parsed sources, rather than guessing a naming convention. */
public final class BasePackages {

    private BasePackages() {
    }

    public static String derive(List<SourceUnit> sourceUnits) {
        if (sourceUnits == null || sourceUnits.isEmpty()) {
            return "";
        }
        List<String> common = null;
        for (SourceUnit sourceUnit : sourceUnits) {
            List<String> segments = segments(sourceUnit);
            common = common == null ? segments : commonPrefix(common, segments);
            if (common.isEmpty()) {
                return "";
            }
        }
        return String.join(".", common);
    }

    private static List<String> segments(SourceUnit sourceUnit) {
        return sourceUnit.unit().getPackageDeclaration()
                .map(PackageDeclaration::getNameAsString)
                .map(name -> List.of(name.split("\\.")))
                .orElse(List.of());
    }

    private static List<String> commonPrefix(List<String> first, List<String> second) {
        int limit = Math.min(first.size(), second.size());
        List<String> prefix = new ArrayList<>();
        for (int index = 0; index < limit; index++) {
            if (!first.get(index).equals(second.get(index))) {
                break;
            }
            prefix.add(first.get(index));
        }
        return prefix;
    }
}
