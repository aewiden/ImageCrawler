package com.eulerity.hackathon.imagefinder;

import java.io.IOException;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@WebServlet(
        name = "ImageFinder",
        urlPatterns = {"/main"}
)
public class ImageFinder extends HttpServlet {
    private static final long serialVersionUID = 1L;
    protected static final Gson GSON = new GsonBuilder().create();

    @Override
    protected final void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		System.out.println("Servlet doPost invoked ");
        resp.setContentType("text/json");
        String path = req.getServletPath();
        String url = req.getParameter("url");
        System.out.println("Got request of:" + path + " with query param:" + url);
		System.out.println(url);

        // Initialize the web crawler
        try(WebScraper webScraper = new WebScraper()) {

        // Scrape the website and get the crawled URLs and images
        Set<String> crawledUrls = webScraper.scrapeWebsite(url);
        Set<String> crawledImages = webScraper.getScrapedImages();

        // Convert the results to JSON and send the response
        resp.getWriter().print(GSON.toJson(new CrawlingResult(crawledUrls, crawledImages)));
	}
    }

    // Helper class to represent the crawling result
    private static class CrawlingResult {
        Set<String> crawledUrls;
        Set<String> crawledImages;

        public CrawlingResult(Set<String> crawledUrls, Set<String> crawledImages) {
            this.crawledUrls = crawledUrls;
            this.crawledImages = crawledImages;
        }
    }
}
