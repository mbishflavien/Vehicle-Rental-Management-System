package com.vrms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.time.Duration;

/**
 * Serves the built React app from classpath:/static. Real files (JS, CSS, images) are returned as-is;
 * any other path that isn't under /api, such as /fleet or /admin/contracts after a page refresh,
 * returns index.html so the client-side router can take over.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Vite fingerprints these file names (index-BW7l6BX0.css), so browsers may keep them for a year.
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

        // index.html and client-side routes must always be revalidated so new releases show up.
        registry.addResourceHandler("/**")
                .setCacheControl(CacheControl.noCache())
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        Resource file = location.createRelative(path);
                        if (file.exists() && file.isReadable()) {
                            return file;
                        }
                        if (path.startsWith("api/") || path.contains(".")) {
                            return null;
                        }
                        Resource index = new ClassPathResource("/static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
