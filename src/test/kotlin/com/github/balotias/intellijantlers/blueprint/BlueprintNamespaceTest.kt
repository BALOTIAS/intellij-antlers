package com.github.balotias.intellijantlers.blueprint

import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class BlueprintNamespaceTest {

    @Test fun collectionFromDir() {
        assertEquals(
            BlueprintNamespace(Kind.COLLECTION, "blog"),
            BlueprintNamespace.fromPath("/proj/resources/blueprints/collections/blog/article.yaml")
        )
    }

    @Test fun taxonomyFromDir() {
        assertEquals(
            BlueprintNamespace(Kind.TAXONOMY, "tags"),
            BlueprintNamespace.fromPath("/proj/resources/blueprints/taxonomies/tags/tags.yaml")
        )
    }

    @Test fun formFromFile() {
        assertEquals(
            BlueprintNamespace(Kind.FORM, "contact"),
            BlueprintNamespace.fromPath("/proj/resources/blueprints/forms/contact.yaml")
        )
    }

    @Test fun assetFromFile() {
        assertEquals(
            BlueprintNamespace(Kind.ASSET, "main"),
            BlueprintNamespace.fromPath("/proj/resources/blueprints/assets/main.yaml")
        )
    }

    @Test fun globalFromFile() {
        assertEquals(
            BlueprintNamespace(Kind.GLOBAL, "site"),
            BlueprintNamespace.fromPath("/proj/resources/blueprints/globals/site.yaml")
        )
    }

    @Test fun userSingleton() {
        assertEquals(
            BlueprintNamespace(Kind.USER, "user"),
            BlueprintNamespace.fromPath("/proj/resources/blueprints/user.yaml")
        )
    }

    @Test fun fieldsetFromFile() {
        assertEquals(
            BlueprintNamespace(Kind.FIELDSET, "seo"),
            BlueprintNamespace.fromPath("/proj/resources/fieldsets/seo.yaml")
        )
    }

    @Test fun unrecognizedIsUnknown() {
        assertEquals(
            BlueprintNamespace(Kind.UNKNOWN, ""),
            BlueprintNamespace.fromPath("/proj/resources/blueprints/something_else/x.yaml")
        )
    }

    @Test fun navigationFromFile() {
        assertEquals(
            BlueprintNamespace(Kind.NAVIGATION, "main"),
            BlueprintNamespace.fromPath("/proj/resources/blueprints/navigation/main.yaml")
        )
    }
}
