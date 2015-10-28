package grails.plugin.wschat.users

import grails.converters.JSON
import grails.plugin.wschat.ChatBanList
import grails.plugin.wschat.ChatBlockList
import grails.plugin.wschat.ChatFriendList
import grails.plugin.wschat.ChatUser
import grails.plugin.wschat.ChatUserProfile
import grails.plugin.wschat.WsChatConfService
import grails.plugin.wschat.ChatMessage
import grails.transaction.Transactional
import groovy.time.TimeCategory
import java.text.SimpleDateFormat
import javax.websocket.Session


class WsChatUserService extends WsChatConfService  {

	def wsChatMessagingService

	Boolean isConfLiveAdmin(String username) {
		if (config.liveChatUsername && config.liveChatUsername==username) {
			return true
		}
	}

	Boolean isLiveAdmin(String username) {
		boolean liveAdminChecked = false
		if (config.liveChatUsername && config.liveChatUsername==username) {
			liveAdminChecked = true
		} else {
			def cu = ChatUser.findByUsername(username)
			if (cu) {
				if (cu.permissions.name == config.liveChatPerm?config.liveChatPerm:config.defaultperm ) {
					liveAdminChecked = true
				}
			}
		}
		return liveAdminChecked
	}

	void kickUser(Session userSession,String username) {
		Boolean useris = isAdmin(userSession)
		if (useris) {
			logoutUser(userSession,username)
		}
	}


	@Transactional
	ArrayList findLogs(String username) {
		ChatUser ccb = ChatUser.findByUsername(username)
		Map resultSet = [:]
		ArrayList finalResults=[]
		if (ccb) {
			def cm = ChatMessage.findAllByLog(ccb.log)
			cm?.each {
				resultSet = [:]
				resultSet << [ message: it.contents, date: it.dateCreated, user: it.user ]
				finalResults << resultSet
			}
		}
		return finalResults
	}


	Map findaUser(String uid) {
		def returnResult=[:]
		def found=ChatUser.findByUsername(uid)
		if (found) {
			returnResult.put("status", "found")
			def foundProfile=ChatUserProfile?.findByChatuser(found)
			if (foundProfile) {
				if (foundProfile?.email)  {
					returnResult.put("email", foundProfile.email)
				}
			}

		}else{
			returnResult.put("status", "not_found")
		}
		return returnResult
	}

	Map search(String mq) {
		def userList = ChatUser?.findAllByUsernameLike("%" + mq + "%", [max: 30])
		def uList = genAllUsers()
		if (!userList) {
			userList = ChatUserProfile.findAllByFirstNameLikeOrEmailLikeOrLastNameLike("%" + mq + "%", "%" + mq + "%", "%" + mq + "%", [max: 30])*.chatuser.unique()
		}
		return [userList:userList, uList:uList]
	}

	private void banUser(Session userSession,String username,String duration,String period) {
		Boolean useris = isAdmin(userSession)
		if (useris) {
			banthisUser(username,duration,period)
			logoutUser(userSession,username)
		}
	}

	private void logoutUser(String username) {
		def myMsg = [:]
		myMsg.put("message", "${username} about to be kicked off")
		chatNames.each { String cuser,Map<String,Session> records ->
			records?.each { String room, Session crec ->
				if (crec && crec.isOpen()) {
					def uList = []
					def finalList = [:]
					if (cuser.equals(username)) {
						def myMsg1 = [:]
						myMsg1.put("system", "disconnect")
						wsChatMessagingService.messageUser(crec, myMsg1)
					}
				}
			}
		}
	}

	private void logoutUser(Session userSession,String username) {
		def myMsg = [:]
		myMsg.put("message", "${username} about to be kicked off")
		wsChatMessagingService.broadcast(userSession,myMsg)
		String room = userSession.userProperties.get("room") as String
		chatNames.each { String cuser, Map<String, Session> records ->
			Session crec = records.find { it.key == room }?.value
			if (crec && crec.isOpen()) {
				def uList = []
				def finalList = [:]
				if (cuser.equals(username)) {
					def myMsg1 = [:]
					myMsg1.put("system", "disconnect")
					wsChatMessagingService.messageUser(crec, myMsg1)
				}
			}
		}
	}

	Session usersSession(String username, String room) {
		return getChatUser(username, room)
	}

	boolean findUser(String username) {
		return chatUserExists(username)
	}

	ArrayList genAllUsers() {
		def uList = []
		chatNames.each { String cuser,Map<String,Session> records ->
			records?.each { String room, Session crec ->
				if (crec && crec.isOpen()) {
					uList.add(cuser)
				}
			}
		}
		return uList
	}

	void sendFlatUsers(Session userSession,String username) {
		userListGen(userSession, username, "flat")
	}

	void sendUsers(Session userSession,String username, String room) {
		String uiterator = userSession.userProperties.get("username").toString()
		userListGen(userSession, username, "generic", room)
	}

	@Transactional
	private void userListGen(Session userSession,String username, String listType, String room) {
		///String sessionRoom  =  userSession.userProperties.get("room") as String
		chatNames.each { String uiterator, Map<String,Session>records ->
			records?.each { String userRoom, Session crec2 ->
				if (userRoom == room && crec2 && crec2.isOpen()) {
					def finalList = [:]
					def	blocklist = ChatBlockList.findAllByChatuser(currentUser(uiterator))
					def	friendslist = ChatFriendList.findAllByChatuser(currentUser(uiterator))
					def	uList = genUserMenu(friendslist, blocklist, room, uiterator, listType)
					if (listType=="generic") {
						finalList.put("users", uList)
					}else{
						finalList.put("flatusers", uList)
					}
					sendUserList(uiterator,finalList,room)
				}
			}
			// Additional work to ensure friends that are in a different room get an updated list
			// this is done to update friends list which is part of over all user listing
			def	friendslist = ChatFriendList.findAllByChatuser(currentUser(username))
			friendslist?.each { ChatFriendList fl->
				Map<String,Session> friendRecords = chatroomUsers.get(fl.username)
				def	blocklist = ChatBlockList.findAllByChatuser(currentUser(fl.username))
				def	otherlist = ChatFriendList.findAllByChatuser(currentUser(fl.username))
				friendRecords.each {String friendRoom, Session friendSession ->
					def finalList = [:]
					def	fList = genUserMenu(otherlist, blocklist, friendRoom, fl.username, listType)
					if (listType=="generic") {
						finalList.put("users", fList)
					}else{
						finalList.put("flatusers", fList)
					}
					sendUserList(fl.username,finalList,friendRoom)
				}
			}
		}
	}

	private ArrayList genUserMenu(ArrayList friendslist, ArrayList blocklist, String room, String uiterator, String listType ) {
		def uList = []
		def vList = []
		chatNames.each { String cuser, Map<String,Session> records ->
			vList.add(cuser)
			Session crec = records.find{it.key==room}?.value
			if (crec && crec.isOpen()) {
				def myUser = [:]
				if (room.equals(crec.userProperties.get("room"))) {
					String av = crec.userProperties.get("av").toString()
					String rtc = crec.userProperties.get("rtc").toString()
					String file = crec.userProperties.get("file").toString()
					String media = crec.userProperties.get("media").toString()
					String game = crec.userProperties.get("game").toString()
					String addav = ""
					if (listType=="generic") {
						if (av.equals("on")) {
							addav = "_av"
						}
						if (rtc.equals("on")) {
							addav = "_rtc"
						}
						if (game.equals('on')) {
							addav = "_game"
						}
						if (file.equals('on')) {
							addav = "_file"
						}
						if (media.equals('on')) {
							addav = "_mediastream"
						}
						if (cuser.equals(uiterator)) {
							myUser.put("owner${addav}", cuser)
							uList.add(myUser)
						}else{
							if ((blocklist)&&(blocklist.username.contains(cuser))) {
								myUser.put("blocked", cuser)
								uList.add(myUser)
							}else if  ((friendslist)&&(friendslist.username.contains(cuser))) {
								myUser.put("friends${addav}", cuser)
								uList.add(myUser)
							}else{
								myUser.put("user${addav}", cuser)
								uList.add(myUser)
							}
						}
					}else{
						myUser.put("users", cuser)
						uList.add(myUser)
					}
				}
			}
		}
		if (friendslist) {
			String method='offline_friends'
			friendslist.each { ChatFriendList fl->
				def myUser1 = [:]
				if (vList.contains(fl.username)) {
					method="online_friends"
				}
				myUser1.put(method, fl.username)
				uList.add(myUser1)
			}
		}

		return uList
	}

	Boolean validateAdmin(String username) {
		boolean useris = false
		def found=ChatUser.findByUsername(username)
		if (found) {
			if (found.permissions.name.toString().toLowerCase().startsWith('admin')) {
				useris = true
			}
		}
		return useris
	}

	@Transactional
	ChatUser currentUser(String username) {
			ChatUser cu =  ChatUser.findByUsername(username)
			return cu
	}

	private void sendUserList(String iuser,Map msg, String room) {
		String sendUserList = config.send.userList  ?: 'yes'
		if ( sendUserList == 'yes') {
			def myMsgj = msg as JSON
			Session crec = getChatUser(iuser,room)
			if (crec && crec.isOpen()) {
				crec.basicRemote.sendText(myMsgj as String)
			}
		}
	}

	private void removeUser(String username) {
		destroyChatUser(username)
	}

	@Transactional
	private void unblockUser(String username,String urecord) {
		def cuser = currentUser(username)
		def found = ChatBlockList.findByChatuserAndUsername(cuser,urecord)
		found.delete(flush: true)
	}

	@Transactional
	private void banthisUser(String username,String duration, String period) {
		def cc
		use(TimeCategory) {
			cc = new Date() +(duration as int)."${period}"
		}
		def current  =  new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss").format(cc)
		def found = ChatBanList.findByUsername(username)
		if (!found) {
			def newEntry = new ChatBanList()
			newEntry.username = username
			newEntry.period = current
			if (!newEntry.save(flush:true)) {
				log.error "Error saving ${newEntry.errors}"
			}
		}else{
			found.period = current
			if (!found.save(flush:true)) {
				log.error "Error saving ${found.errors}"
			}
		}
	}

	@Transactional
	private void blockUser(String username,String urecord) {
		def cuser = currentUser(username)
		def found = ChatBlockList.findByChatuserAndUsername(cuser,urecord)
		if (!found) {
			def newEntry = new ChatBlockList()
			newEntry.chatuser = cuser
			newEntry.username = urecord
			if (!newEntry.save(flush:true)) {
				log.error "Error saving ${newEntry.errors}"
			}
		}
	}

	@Transactional
	private void addUser(String username,String urecord) {
		def cuser = currentUser(username)
		def found = ChatFriendList.findByChatuserAndUsername(cuser, urecord)
		if (!found) {
			def newEntry = new ChatFriendList()
			newEntry.chatuser = cuser
			newEntry.username = urecord
			if (!newEntry.save(flush:true)) {
				log.error "Error saving ${newEntry.errors}"
			}
		}
	}

	@Transactional
	private void removeUser(String username,String urecord) {
		def cuser = currentUser(username)
		def found = ChatFriendList.findByChatuserAndUsername(cuser,urecord)
		found.delete(flush: true)
	}

	private String getCurrentUserName(Session userSession) {
		def myMsg = [:]
		String username = userSession.userProperties.get("username") as String
		if (!username) {
			myMsg.put("message","Access denied no username defined")
			wsChatMessagingService.messageUser(userSession,myMsg)
		}else{
			return username
		}
	}
}
