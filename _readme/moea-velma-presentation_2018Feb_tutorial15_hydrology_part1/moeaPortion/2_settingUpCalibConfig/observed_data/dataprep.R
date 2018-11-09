

setwd("L://Priv//CORFiles//Projects//VELMA//0_VELMA Projects//Case Studies//Seattle//Longfellow//DataInputs//m_7_Observed//Calibration")


filename <- "STA098A_noheader.tsf"

#Read in time series data for flow from SPU
out = read.table(filename)

#Calculate Daily Averages of Depth and Discharge
aveDepth = aggregate(out$V4,by=list(as.Date(out$V1,"%m/%d/%Y")),FUN=mean)
aveCFS = aggregate(out$V5,by=list(as.Date(out$V1,"%m/%d/%Y")),FUN=mean)

#Convert CFS to mm/day
aveCMS = data.frame(aveCFS$Group.1,((aveCFS$x*0.028)/ 9853300)*86400*1000)
names(aveCMS) <- c('dates','mm/day')

#Write VELMA output file. 
write.csv(aveCMS,paste("velma_daily_mmday_",filename,".csv",sep=''),row.names=F)



setwd("../../MOEA_Files/")
out = read.csv('velma_daily_mmday_STA098A.csv')
jday = format(as.Date(out$dates),'%j')
year = format(as.Date(out$dates),'%Y')
test = data.frame(year,jday,out$mm.day)
write.csv(test,"velma_daily_mmday_STA098A_formatted.csv",row.names=F)

