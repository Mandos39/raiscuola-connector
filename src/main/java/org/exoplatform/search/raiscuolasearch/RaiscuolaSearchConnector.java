package org.exoplatform.search.raiscuolasearch;

import org.exoplatform.commons.api.search.SearchServiceConnector;
import org.exoplatform.commons.api.search.data.SearchContext;
import org.exoplatform.commons.api.search.data.SearchResult;
import org.exoplatform.container.xml.InitParams;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import java.util.*;

public class RaiscuolaSearchConnector extends SearchServiceConnector {

    private HashMap<String, String> categoryLabelMap;
    private String[] raiLabelMap;
    private String raiHostname;
    private Collection<SearchResult> globalResults;
    private String oldQuery;

    public RaiscuolaSearchConnector(InitParams initParams) {
        super(initParams);

        categoryLabelMap = new HashMap<String, String>();
        categoryLabelMap.put("VI", "Video");
        categoryLabelMap.put("CA", "Speciale");
        categoryLabelMap.put("AR", "Articoli");
        categoryLabelMap.put("PL", "LessonPlan");
        categoryLabelMap.put("PR", "Programmi");

        raiLabelMap = new String[]{"Letteratura", "Arte&Design", "Scuola", "Storia",
                "Filosofia", "Economia", "Italiano", "Media"};

        raiHostname = "http://www.raiscuola.rai.it";

        globalResults = null;
        oldQuery = null;
    }

    private Elements documentToElements(Document doc) {
        try {
            Element article = doc.body().getElementById("main-left").child(0);
            return article.children();
        } catch (NullPointerException e) {
            return null;
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    private SearchResult elementToSearchResult(Element el) {

        String url;
        String title;
        String text;
        String imageUrl;
        String categoryLabel;
        String raiLabel;

        try {
            Element linkEl = el.child(0);

            Element titleEl = linkEl.child(2);
            title = titleEl.text();
            if (title.length() > 0) {
                //rendiamo maiuscola la prima lettera e minuscole le altre
                title = title.substring(0, 1).toUpperCase()
                        + title.substring(1).toLowerCase();
            }

            Element categoryEl = linkEl.child(0).child(0);
            try {
                String abbr = categoryEl.attr("src").substring(14, 16);
                categoryLabel = categoryLabelMap.get(abbr);
            } catch (IndexOutOfBoundsException e) {
                categoryLabel = "";
            }

            Element raiEl = linkEl.child(2).child(0);
            try {
                String index = raiEl.attr("src").substring(12, 13);
                raiLabel = "Rai " + raiLabelMap[Integer.parseInt(index) - 1];
            } catch (IndexOutOfBoundsException e) {
                raiLabel = "";
            }

            // alcuni link sono soltanto relativi, e devono essere completati
            url = linkEl.attr("href");
            if (url.startsWith("/")) {
                url = raiHostname + url;
            }

            // tutti i link della categoria LessonPlan sono sbagliati
            // ma con questa modifica funzionano
            if (categoryLabel.equals("LessonPlan")) {
                url = url.replaceFirst("lessonplan", "lezione");
                url = url.replaceFirst("/1/", "/");
            }

            Element textEl = linkEl.child(3);
            text = textEl.text();

            Element imageEl = linkEl.child(1).child(0);
            imageUrl = imageEl.attr("src");

            return new SearchResult(
                    url,
                    title,
                    text,
                    categoryLabel + " - " + raiLabel,
                    imageUrl,
                    new Date().getTime(),
                    1L
            );
        } catch (NullPointerException e) {
            return null;
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    private Collection<SearchResult> searchAll(String query) {

        int page = 0;
        Collection<SearchResult> results = new ArrayList<SearchResult>();
        Connection connection;
        Document doc;

        try {
            query = URLEncoder.encode(query, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return results;
        }

        String url = raiHostname + "/cerca.aspx?s=" + query + "&pagina=";
        connection = Jsoup.connect(raiHostname);
        Elements elements;
        do {
            page++;
            connection.url(url + page);

            try {
                doc = connection.get();
            } catch (IOException e) {
                return results;
            }

            elements = documentToElements(doc);
            for (Element el : elements) {
                results.add(elementToSearchResult(el));
            }

        }
        while (!elements.isEmpty() && page < 10);
        return results;
    }


    @Override
    public Collection<SearchResult> search(SearchContext context, String query,
                                           Collection<String> sites, int offset, int limit,
                                           String sort, String order) {
        Collection<SearchResult> results = new ArrayList<SearchResult>();

        if (!query.equals(oldQuery)) {
            globalResults = null;
            oldQuery = query;
        }

        try {
            if ("title".equals(sort)) {
                if (globalResults == null) {
                    results = searchAll(query);
                    globalResults = results;
                } else {
                    results = globalResults;
                }
                if ("asc".equals(order)) {
                    Collections.sort((List<SearchResult>) results,
                            new Comparator<SearchResult>() {
                                public int compare(SearchResult sr1, SearchResult sr2) {
                                    return sr1.getTitle().compareTo(sr2.getTitle());
                                }
                            }
                    );
                } else {
                    Collections.sort((List<SearchResult>) results,
                            new Comparator<SearchResult>() {
                                public int compare(SearchResult sr1, SearchResult sr2) {
                                    return sr2.getTitle().compareTo(sr1.getTitle());
                                }
                            }
                    );
                }
                if (offset > results.size()) {
                    return null;
                }
                return ((List<SearchResult>) results).subList(offset,
                        (offset + limit) > results.size() ? results.size()
                                : offset + limit);
            }

            Document doc;
            Elements elements;
            Connection connection;
            String url;
            try {
                query = URLEncoder.encode(query, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                return results;
            }
            int page = (offset / 10);
            int initial = offset % 10;
            int current = 0;
            url = raiHostname + "/cerca.aspx?s=" + query + "&pagina=";
            connection = Jsoup.connect(raiHostname);
            do {
                page++;
                connection.url(url + page);
                try {
                    doc = connection.get();
                } catch (IOException e) {
                    return results;
                }
                elements = documentToElements(doc);
                for (int i = initial; i < elements.size() && current < limit; i++) {
                    results.add(elementToSearchResult(elements.get(i)));
                    current++;
                }
                initial = 0;
            }
            while (current < limit && !elements.isEmpty());
            return results;
        } catch (NullPointerException e) {
            return results;
        }
    }
}