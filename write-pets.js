const fs = require('fs');
const utf8 = {encoding:'utf8'};

function writePets() { fs.writeFileSync('E:/MapleLeaf/project/MiaoWang/front-project/pages/pets/list.uvue',
\<template>
\t<view class="pl-page">
\t\t<view class="pl-header"><text class="pl-title">我的萌宠</text></view>
\t\t<scroll-view scroll-y="true" class="pl-list">
\t\t\t<view class="pl-card" v-for="pet in pets" :key="pet.id">
\t\t\t\t<view class="pl-top">
\t\t\t\t\t<text class="pl-avatar">🐱</text>
\t\t\t\t\t<view class="pl-info">
\t\t\t\t\t\t<text class="pl-name">{{ pet.name }}</text>
\t\t\t\t\t\t<text class="pl-breed">{{ pet.breedName !== "" ? pet.breedName : "未知品种" }}</text>
\t\t\t\t\t\t<text class="pl-detail">{{ pet.gender === 1 ? "公" : "母" }} | {{ pet.age }} 岁 | {{ pet.weight }}kg</text>
\t\t\t\t\t</view>
\t\t\t\t</view>
\t\t\t\t<view class="pl-care">
\t\t\t\t\t<view class="pl-care-item" v-for="care in pet.cares" :key="care.id">
\t\t\t\t\t\t<text class="pl-care-name">{{ care.typeName }}</text>
\t\t\t\t\t\t<text class="pl-care-date">{{ care.nextDate }}</text>
\t\t\t\t\t</view>
\t\t\t\t</view>
\t\t\t</view>
\t\t</scroll-view>
\t\t<view class="pl-add" @click="addPet"><text class="pl-add-text">+ 添加宠物</text></view>
\t</view>
</template>

<script setup lang="uts">
const BASE = "http://localhost:8080"

type CareData = { id: number; typeName: string; nextDate: string }
type PetData = { id: number; name: string; breedName: string; gender: number; age: number; weight: number; cares: CareData[] }

const pets = ref<PetData[]>([])
function getToken(): string { const t = uni.getStorageSync("token"); return t != null ? t as string : "" }
function loadPets(): void {
\tconst token = getToken()
\tconst h = new UTSJSONObject()
\th["Content-Type"] = "application/json"
\tif (token !== "") h["Authorization"] = "Bearer " + token
\tuni.request({ url: BASE + "/api/pets", method: "GET", header: h,
\t\tsuccess: (res): void => {
\t\t\tconst d = res.data; if (d != null) {
\t\t\t\tconst body = d as UTSJSONObject
\t\t\t\tif (body.getNumber("code") === 200) { const inner = body.get("data"); if (inner != null) pets.value = inner as any as PetData[] }
\t\t\t}
\t\t}, fail: (): void => {}
\t})
}
function addPet(): void { uni.showToast({ title: "即将上线", icon: "none" }) }
onShow(() => { loadPets() })
</script>

<style>
.pl-page { background-color: #FFF8F0; flex: 1; }
.pl-header { padding: 32rpx 28rpx 16rpx; }
.pl-title { font-size: 40rpx; font-weight: 700; color: #333; }
.pl-list { flex: 1; padding: 8rpx 0; }
.pl-card { margin: 12rpx 24rpx; padding: 24rpx; background-color: #FFF; border-radius: 18rpx; }
.pl-top { flex-direction: row; align-items: center; margin-bottom: 16rpx; }
.pl-avatar { font-size: 72rpx; margin-right: 20rpx; }
.pl-info { flex: 1; flex-direction: column; }
.pl-name { font-size: 32rpx; font-weight: 700; color: #333; }
.pl-breed { font-size: 24rpx; color: #999; margin-top: 4rpx; }
.pl-detail { font-size: 22rpx; color: #666; margin-top: 4rpx; }
.pl-care { flex-direction: row; padding-top: 16rpx; border-top-width: 1rpx; border-top-color: #F0F0F0; }
.pl-care-item { flex-direction: column; margin-right: 28rpx; align-items: center; }
.pl-care-name { font-size: 22rpx; color: #FF8C42; }
.pl-care-date { font-size: 20rpx; color: #999; margin-top: 2rpx; }
.pl-add { margin: 20rpx 24rpx 40rpx; padding: 20rpx; background-color: #FF8C42; border-radius: 16rpx; align-items: center; }
.pl-add-text { font-size: 28rpx; color: #FFF; font-weight: 600; }
</style>\, utf8); }
writePets(); console.log('pets done');