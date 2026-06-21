alter table question
    add column question_media_object_key text,
    add column question_media_content_type varchar(128);

alter table question
    alter column question_text drop not null;

alter table question_option
    add column option_media_object_key text,
    add column option_media_content_type varchar(128);

alter table question_option
    alter column option_text drop not null;

alter table question_answer
    add column answer_media_object_key text,
    add column answer_media_content_type varchar(128);

alter table question_answer
    alter column answer_value drop not null;
