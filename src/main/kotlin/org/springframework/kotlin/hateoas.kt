package org.springframework.kotlin

import org.slf4j.LoggerFactory
import org.springframework.hateoas.Link
import org.springframework.hateoas.PagedResources
import org.springframework.hateoas.Resource
import org.springframework.hateoas.ResourceSupport
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

fun <R: ResourceSupport> R?.link(linkName: String, mappingName: String, params: Array<out String?>): R? {
    var i = 0
    try {
        val argumentBuilder = MvcUriComponentsBuilder.fromMappingName(mappingName)
        val href = params.fold(argumentBuilder, { builder, p -> if (p != null) builder.arg(i++, p) else builder })
        val normalizedHref = href
            .build()
            .replace("\\?.+$".toRegex(), "") // strip query parameters, which should have sensible defaults anyway
            .replace("/:([^/]+)".toRegex(), "/{$1}") // replace :variable style templates with {variable} style templates
        this?.add(Link(normalizedHref).withRel(linkName))
    } catch(e: IllegalArgumentException) {
        // if lookup handler methods cannot be looked up because there is no WebApplicationContext, just do nothing
        LoggerFactory.getLogger("hateoas").warn("unable to find request mapping for '{}', is the name attribute defined on the intended target endpoint?", mappingName)
    }
    return this
}

/**
 * @return - resolves mapping name with params and adds a link to the resource; fails quietly with a no-op if the mapping cannot be found
 */
fun <R: ResourceSupport> R?.link(linkName: String, vararg params: String?): R? = link(linkName, linkName, params)

/**
 * @return - resolves mapping name with params and adds a self link to the resource; fails quietly with a no-op if the mapping cannot be found
 */
fun <R: ResourceSupport> R?.selfLink(mappingName: String, vararg params: String?): R? = link("self", mappingName, params)

fun <T> Collection<T?>.toPagedResources(): PagedResources<Resource<T>> {
    var queryParams = emptyMap<String, Collection<String>>()
    try {
        queryParams = ServletUriComponentsBuilder.fromCurrentRequest().build().queryParams
    } catch(ignored: IllegalStateException) {
        // could not find current request via RequestContextHolder, that's ok...
    }

    val limit = (queryParams["limit"] ?: listOf("100")).first().toInt()
    val pageNum = (queryParams["page"] ?: listOf("1")).first().toInt()

    val page = this.filter { it != null }.drop((pageNum - 1) * limit).take(limit)
    return PagedResources.wrap<Resource<T>, T>(page, PagedResources.PageMetadata(limit.toLong(),
            pageNum.toLong(), size.toLong()))!!
}

fun <T: Any> T?.toResource(selfMapping: String, id: (T) -> String): Resource<T>? = if(this != null) Resource(this).selfLink(selfMapping, id(this)) else null

fun <T: Any> T?.toResource(): Resource<T>? = if(this != null) Resource(this) else null

fun <T, P: PagedResources<Resource<T>>> P.linkEach(linkName: String, mappingName: String, vararg params: (T) -> String): P {
    content.forEach { r ->
        r.link(linkName, mappingName, params.map { it.invoke(r.content) }.toTypedArray())
    }
    return this
}

fun <T, P: PagedResources<Resource<T>>> P.linkEach(linkName: String, vararg params: (T) -> String): P =
        linkEach(linkName, linkName, *params)

fun <T: ResourceSupport> T?.linkStatic(linkName: String, link: (T) -> String): T? {
    this?.add(Link(link.invoke(this)).withRel(linkName))
    return this
}

fun <T, P: PagedResources<Resource<T>>> P.linkEachStatic(linkName: String, link: (T) -> String): P {
    content.forEach { r ->
        r.add(Link(link.invoke(r.content)).withRel(linkName))
    }
    return this
}