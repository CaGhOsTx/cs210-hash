package carlos.webcrawler;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static carlos.webcrawler.ContentHandler.saveContent;
import static carlos.webcrawler.Options.*;
import static java.lang.Thread.currentThread;
import static java.util.Collections.newSetFromMap;

public final class WebScraper implements Serializable {
    @Serial
    private static final long serialVersionUID = 5440710515833287425L;
    static int ID;
    private final Set<String> visitedLinks;
    private final Queue<String> unvisitedLinks;
    private final OptionHandler optionHandler;
    private final ContentHandler contentHandler;
    private transient threadHandler threadHandler;
    private final String startURL;
    private final int DATA_LIMIT;
    private static final int LINK_CACHE_LIMIT = 1_000_000, DATA_CACHE_LIMIT = 500_000;


    WebScraper(String startURL, OptionHandler optionHandler, ContentHandler contentHandler, threadHandler threadHandler, int limit) {
        this.startURL = startURL;
        this.optionHandler = optionHandler;
        this.contentHandler = contentHandler;
        this.threadHandler = threadHandler;
        visitedLinks = newSetFromMap(new ConcurrentHashMap<>());
        unvisitedLinks = new ConcurrentLinkedQueue<>();
        ++ID;
        DATA_LIMIT = limit;
    }

    public WebScraper start() {
        try {
            addUnvisitedLinksToQueue(visitAndRetrieveHTML(startURL), startURL);
            threadHandler.setTask(this::main).start();
        } catch(PageWithoutLinksException e) {
            System.err.println("UNABLE TO START " + this);
        }
        return this;
    }

    public synchronized Set<String> getContent(ContentType gc, String html) {
        return contentHandler.getContent(gc, html);
    }

    public void stop() {
        threadHandler.stop();
    }
    public void close() {
        try {
            if(optionHandler.isTrue(DEBUG_MODE)) printFinished();
            if(optionHandler.isTrue(SAVE_LINKS)) saveLinks();
            if(optionHandler.isTrue(SAVE_CONTENT)) saveAllContent();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return "WebScraper::" + ID + " (started from " + startURL + ")";
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        threadHandler.stop();
        out.defaultWriteObject();
        out.writeObject(threadHandler.size());
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        threadHandler = new threadHandler((int) in.readObject());
        threadHandler.setTask(this::main).start();
    }

    private synchronized void saveAllContent() throws IOException {
        for(var ct : contentHandler.getTypes())
            saveContent(ct);
    }

    private synchronized void flushContent() {
        for (var ct : contentHandler.getTypes())
            if (ct.getData().size() >= DATA_CACHE_LIMIT)
                saveAndClear(ct);
    }

    private synchronized void saveAndClear(ContentType ct) {
        try {
            saveContent(ct);
            ct.clearData();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private void saveLinks() throws IOException {
        contentHandler.saveLinks(visitedLinks);
        contentHandler.saveLinks(unvisitedLinks);
    }

    private void printFinished() {
        System.out.println("--------------");
        System.out.println(this + " FINISHED!");
        System.out.println("--------------");
    }

    private void main() {
        while (optionHandler.isTrue(UNLIMITED) || contentHandler.notAllAreCollected(DATA_LIMIT)) {
            var link = nextLink();
            String html = visitAndRetrieveHTML(link);
            if(unvisitedLinks.size() < LINK_CACHE_LIMIT)
                tryToAddNewLinks(link, html);
            for(var ct : contentHandler.getTypes()) {
                addContentDataAndUpdateCount(html, ct);
                printDebugMain(ct);
            }
            if (threadHandler.shouldStop())
                break;
        }
        System.out.println(Thread.currentThread() + " stopping");
        if(threadHandler.isLastThread()) close();
    }

    private void tryToAddNewLinks(String link, String html) {
        try {
            addUnvisitedLinksToQueue(html, link);
        } catch(PageWithoutLinksException e) {
            if(optionHandler.isTrue(DEBUG_MODE)) System.err.println(e.getMessage());
        }
    }

    private synchronized void addContentDataAndUpdateCount(String html, ContentType contentType) {
        if (!contentType.reachedLimit(DATA_LIMIT))
            contentType.addData(getContent(contentType, html), DATA_LIMIT);
        flushContent();
    }

    private void printDebugMain(ContentType ct) {
        if(optionHandler.isTrue(DEBUG_MODE)) {
            System.out.println(currentThread().getName());
            System.out.println("accumulated " + ct.NAME + ": " + ct.getCollected());
        }
    }

    private synchronized String nextLink() {
        if(unvisitedLinks.isEmpty())
            throw new IllegalStateException("Reached end!" + "(" + this + ")");
        var link = unvisitedLinks.poll();
        visitedLinks.add(link);
        printDebugRL();
        return link;
    }

    private synchronized void addUnvisitedLinksToQueue(String html, String url) throws PageWithoutLinksException {
        var links = contentHandler.getLinks(html);
        if (links.isEmpty()) throw new PageWithoutLinksException(url);
        links.stream().filter(s -> !visitedLinks.contains(s))
                .forEach(unvisitedLinks::add);
    }

    private void printDebugRL() {
        if(optionHandler.isTrue(DEBUG_MODE)) {
            System.out.println("visited links: " + visitedLinks.size());
            System.out.println("unvisited links: " + unvisitedLinks.size());
        }
    }

    private String visitAndRetrieveHTML(String url) {
        var sb = new StringBuilder();
        boolean error = true;
        while (error) {
            debugGetHTML(url);
            try (var reader = new BufferedInputStream(new URI(url).toURL().openStream())) {
                int b;
                error = false;
                while ((b = reader.read()) != -1)
                    sb.append((char) b);
            } catch (IOException | URISyntaxException e) {
                if (optionHandler.isTrue(DEBUG_MODE)) {
                    System.err.println(e.getMessage());
                    ifTooManyRequestsErrorSleep(e);
                    System.err.println("Couldn't visit page:" + url);
                }
                url = nextLink();
            }
        }
        return sb.toString();
    }

    private void ifTooManyRequestsErrorSleep(Exception e) {
        if(e.getMessage().contains(" 429 ")) {
            try {
                Thread.sleep(30000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void debugGetHTML(String url) {
        if(optionHandler.isTrue(DEBUG_MODE))
            System.out.println("visiting " + url);
    }

    public boolean isRunning() {
        return !threadHandler.allTerminated();
    }

    public String getCollectedInfo() {
        var sb = new StringBuilder();
        sb.append(this);
        return sb.toString();
    }

    private final class PageWithoutLinksException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 7244117343903569290L;
        private final String link;

        PageWithoutLinksException(String link) {
            this.link = link;
        }

        @Override
        public String getMessage() {
            return WebScraper.this + " page has no identifiable links! " + " --LINK TO PAGE -> " + link;
        }
    }
}
