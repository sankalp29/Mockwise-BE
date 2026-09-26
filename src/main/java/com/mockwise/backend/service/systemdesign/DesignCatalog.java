package com.mockwise.backend.service.systemdesign;

import com.mockwise.backend.repository.question.Difficulty;

import java.util.List;

/**
 * Practice prompts for high-level design sessions. Markdown is stored as written.
 */
public final class DesignCatalog {

    public record PromptDraft(String title, Difficulty level, String brief, String requirements, String constraints) {
    }

    private DesignCatalog() {
    }

    public static List<PromptDraft> prompts() {
        return List.of(
                new PromptDraft(
                        "URL Shortener",
                        Difficulty.EASY,
                        """
                        ## URL Shortener

                        Design a service that turns a long URL into a short link and redirects visitors back to the original address.
                        """,
                        """
                        - Create a short link from a long URL
                        - Redirect with the original URL
                        - Show a click count for the owner
                        - Links should keep working for at least one year
                        """,
                        """
                        - 100 million new links per month
                        - Reads are much more common than writes
                        - A short link is 7 characters
                        """
                ),
                new PromptDraft(
                        "News Feed",
                        Difficulty.MEDIUM,
                        """
                        ## News Feed

                        Design the home feed for a social app. A user follows other people and sees their posts, newest first, with likes.
                        """,
                        """
                        - Publish a text or image post
                        - Follow and unfollow
                        - Load the home feed
                        - Like a post
                        """,
                        """
                        - 50 million daily active users
                        - The feed should feel fresh within a few seconds of a post
                        - Celebrity accounts can have millions of followers
                        """
                ),
                new PromptDraft(
                        "Chat Service",
                        Difficulty.HARD,
                        """
                        ## Chat Service

                        Design a one-to-one and group chat product. Messages should arrive while the app is open, and history should survive a refresh.
                        """,
                        """
                        - Send and receive messages in a conversation
                        - Create a group with up to 500 members
                        - Show who is online
                        - Keep message history searchable by the participants
                        """,
                        """
                        - 10 million concurrent connections
                        - Delivery target under 200ms when both people are online
                        - Messages are kept for 2 years
                        """
                )
        );
    }

    public static String sampleScene() {
        return """
                {"type":"excalidraw","version":2,"elements":[{"id":"client","type":"rectangle","x":40,"y":80,"width":160,"height":70,"angle":0,"strokeColor":"#1e1e1e","backgroundColor":"#a5d8ff","fillStyle":"solid","strokeWidth":2,"roughness":1,"opacity":100,"seed":1,"version":1,"versionNonce":1,"isDeleted":false,"updated":1,"link":null,"locked":false},{"id":"api","type":"rectangle","x":280,"y":80,"width":160,"height":70,"angle":0,"strokeColor":"#1e1e1e","backgroundColor":"#b2f2bb","fillStyle":"solid","strokeWidth":2,"roughness":1,"opacity":100,"seed":2,"version":1,"versionNonce":2,"isDeleted":false,"updated":1,"link":null,"locked":false},{"id":"db","type":"rectangle","x":520,"y":80,"width":160,"height":70,"angle":0,"strokeColor":"#1e1e1e","backgroundColor":"#ffec99","fillStyle":"solid","strokeWidth":2,"roughness":1,"opacity":100,"seed":3,"version":1,"versionNonce":3,"isDeleted":false,"updated":1,"link":null,"locked":false}],"appState":{"viewBackgroundColor":"#ffffff"}}
                """;
    }

    public static String sampleReview() {
        return """
                {"problemFraming":{"score":8,"feedback":"The drawing names the client, the API, and the store, which matches the main request path."},"architecture":{"score":8,"feedback":"The boxes are connected in a readable left-to-right flow."},"scalability":{"score":7,"feedback":"A cache in front of the store would absorb the read-heavy traffic called out in the prompt."},"tradeoffs":{"score":7,"feedback":"Call out why the identifier is random instead of sequential."},"communication":{"score":8,"feedback":"The diagram is small and easy to narrate."},"strengths":["Clear request path","Separates the API from storage"],"improvements":["Add a cache for redirects","Note how expired links are removed"],"overallFeedback":"This is a solid first whiteboard for a URL shortener. The core path is there; the next pass should cover read amplification and expiry.","overallRating":8}
                """;
    }
}
