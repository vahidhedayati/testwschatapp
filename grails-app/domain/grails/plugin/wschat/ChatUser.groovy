package grails.plugin.wschat


class ChatUser {

	Date dateCreated
	Date lastUpdated
	String username
	ChatLog log
	ChatLog offlog
	ChatPermissions permissions
	static belongsTo = [profile:ChatUserProfile]
	static hasMany = [photos: ChatUserPics, friends: ChatFriendList, blocked:ChatBlockList ]

	static mapping = {
		permissions lazy: false
		//friends cascade: 'lock'
	}

	static constraints = {
		username blank: false, unique: true
		friends nullable:true
		blocked nullable:true
		photos nullable:true
		profile nullable:true
		log nullable: true
		offlog nullable: true
	}

}
