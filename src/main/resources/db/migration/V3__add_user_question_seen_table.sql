-- Create user_question_seen table to track which questions a user has seen
CREATE TABLE user_question_seen (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR NOT NULL,
    question_id UUID NOT NULL,
    difficulty VARCHAR NOT NULL,
    seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, question_id)
);

-- Create index for efficient querying by user and difficulty
CREATE INDEX idx_user_question_seen_user_difficulty 
ON user_question_seen(user_id, difficulty);

-- Create index for efficient querying by question_id
CREATE INDEX idx_user_question_seen_question_id 
ON user_question_seen(question_id);

-- Add foreign key constraint to questions table
ALTER TABLE user_question_seen 
ADD CONSTRAINT fk_user_question_seen_question_id 
FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE;
