
# JSON examples: MIRO data describing an artwork also in Sierra

Comment, lines 45-54: MIRO data can include both 

* a createdDate field generated by a MIRO cataloguer and,
* a publicationDate field pulled across from Sierra (where appropriate)

Suggest where the latter exists it should take priority?

Comment, lines 58-82: ; Comment: I have not yet set up the Subjects ontology but am assuming that each subject will have a note of its label and its source authority.
In MIRO there are keywords that do not come from any external thesaurus - do we want to display these?  These are at lines 64-82.  Initial feedback suggests that even though the MIRO keywords are undisciplined and perhaps superseded by having better access to Sierra records
describing the source, nonetheless they do provide search targets and we might well want to render them.
NB: the square brackets and weird spacing mirror the way that the data has been entered into MIRO.

Comment, line 97: the status Open (ie can be shown on the web) is generated within MIRO by the combination of five factors: 
* `<all_web_publish>`, `<image_general_use>` and `<image_copyright_cleared>` have Y, 
* `<image_title>` has content, and 
* "cataloguing is complete" - will follow up with MIRO users whether there is a flag for this
