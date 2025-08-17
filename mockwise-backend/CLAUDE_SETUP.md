# Claude Sonnet 4 Integration Setup

## Overview
Your MockWise backend now integrates with Anthropic's Claude Sonnet 4 to provide AI-powered code feedback during interviews.

## Setup Instructions

### 1. Get Claude API Key
1. Visit [Anthropic Console](https://console.anthropic.com/)
2. Create an account or log in
3. Generate an API key
4. Copy the API key (starts with `sk-ant-`)

### 2. Configure Environment Variable
Add your Claude API key to your environment:

**Option A: Environment Variable (Recommended for production)**
```bash
export CLAUDE_API_KEY="your-actual-claude-api-key-here"
```

**Option B: Update application.properties (Development only)**
```properties
claude.api.key=your-actual-claude-api-key-here
```

### 3. Restart Your Application
After setting the API key, restart your Spring Boot application.

## How It Works

### Backend Flow
1. **Interview Start**: User starts interview via `/api/interview/start`
2. **Question Retrieval**: Backend randomly selects questions based on difficulty
3. **Interview Submission**: User submits code via `/api/interview/{id}/submit`
4. **Claude Evaluation**: Backend sends each solution to Claude Sonnet 4 for analysis
5. **Feedback Storage**: Claude's response is stored in the database
6. **Feedback Retrieval**: Frontend fetches feedback via `/api/interview/{id}/feedback`

### Claude Prompt Structure
For each code submission, Claude receives:
- **Problem Statement**: Complete question description, examples, and constraints
- **User's Code**: The submitted solution
- **Evaluation Criteria**: Correctness, time/space complexity, clarity, readability, modularity

### Claude Response Format
Claude returns a structured JSON with:
```json
{
  "correctness": { "score": 0-10, "feedback": "..." },
  "timeComplexity": { "score": 0-10, "feedback": "...", "bigO": "O(n)" },
  "spaceComplexity": { "score": 0-10, "feedback": "...", "bigO": "O(1)" },
  "clarity": { "score": 0-10, "feedback": "..." },
  "modularity": { "score": 0-10, "feedback": "..." },
  "overallRating": 0-10,
  "overallFeedback": "comprehensive summary",
  "strengths": ["list of strengths"],
  "improvements": ["list of improvements"]
}
```

## Frontend Changes

### New Components
- **InterviewFeedback.jsx**: Displays Claude's feedback with visual progress bars
- **Updated InterviewSession.jsx**: Handles interview submission and navigation to feedback

### New Routes
- `/interview/feedback/:interviewId`: Shows detailed feedback for completed interviews

## API Endpoints

### Interview Management
- `POST /api/interview/start`: Start new interview
- `POST /api/interview/{id}/submit`: Submit interview with code solutions
- `GET /api/interview/{id}/feedback`: Retrieve interview feedback
- `GET /api/interview/questions`: Get random questions by difficulty

## Database Changes

### New Entities
- **Question**: Stores interview questions with difficulty levels
- **Interview**: Tracks interview sessions
- **UserSubmission**: Stores user code and Claude feedback

### Sample Data
The application automatically loads sample questions on startup if none exist.

## Troubleshooting

### Common Issues
1. **Missing API Key**: Ensure `CLAUDE_API_KEY` environment variable is set
2. **Network Timeout**: Claude API calls timeout after 30 seconds
3. **Invalid JSON Response**: Fallback responses are provided if Claude returns malformed JSON

### Logging
Enable debug logging to monitor Claude API calls:
```properties
logging.level.com.mockwise.mockwise_backend.interview.ClaudeService=DEBUG
```

## Costs and Rate Limits
- **Claude Sonnet 4**: Check Anthropic's pricing for current rates
- **Rate Limits**: Monitor your usage in the Anthropic Console
- **Async Processing**: Feedback generation happens asynchronously to avoid blocking

## Security Notes
- API keys are loaded from environment variables
- CORS is configured for your frontend domain
- Authentication required for all interview endpoints (except `/questions`)
